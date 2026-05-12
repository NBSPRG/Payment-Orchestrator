package com.yuno.payment.orchestration

import com.yuno.payment.api.v1.dto.CardRequest
import com.yuno.payment.api.v1.dto.CreatePaymentRequest
import com.yuno.payment.api.v1.dto.MoneyRequest
import com.yuno.payment.api.v1.dto.PaymentMethodRequest
import com.yuno.payment.domain.model.PaymentMethodType
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.idempotency.IdempotencyService
import com.yuno.payment.idempotency.RedisPaymentLock
import com.yuno.payment.observability.PaymentMetrics
import com.yuno.payment.persistence.entity.PaymentEntity
import com.yuno.payment.persistence.entity.PaymentStatusHistoryEntity
import com.yuno.payment.persistence.repository.PaymentRepository
import com.yuno.payment.persistence.repository.PaymentStatusHistoryRepository
import com.yuno.payment.provider.model.ProviderResult
import com.yuno.payment.routing.RoutingEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional

class PaymentOrchestrationServiceTest {
    private lateinit var idempotencyService: IdempotencyService
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var historyRepository: PaymentStatusHistoryRepository
    private lateinit var routingEngine: RoutingEngine
    private lateinit var retryCoordinator: RetryCoordinator
    private lateinit var stateMachine: PaymentStateMachine
    private lateinit var outboxPublisher: OutboxPublisher
    private lateinit var transactionTemplate: TransactionTemplate
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var paymentLock: RedisPaymentLock
    private lateinit var paymentMetrics: PaymentMetrics
    private lateinit var service: PaymentOrchestrationService

    @BeforeEach
    fun setup() {
        idempotencyService = mock()
        paymentRepository = mock()
        historyRepository = mock()
        routingEngine = mock()
        retryCoordinator = mock()
        stateMachine = mock()
        outboxPublisher = mock()
        transactionTemplate = mock()
        eventPublisher = mock()
        paymentLock = mock()
        paymentMetrics = mock()

        whenever(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer {
            (it.arguments[0] as TransactionCallback<*>).doInTransaction(mock())
        }
        whenever(paymentRepository.save(any<PaymentEntity>())).thenAnswer { it.arguments[0] }
        whenever(historyRepository.save(any<PaymentStatusHistoryEntity>())).thenAnswer { it.arguments[0] }

        service = PaymentOrchestrationService(
            idempotencyService,
            paymentRepository,
            historyRepository,
            routingEngine,
            retryCoordinator,
            stateMachine,
            outboxPublisher,
            transactionTemplate,
            eventPublisher,
            paymentLock,
            paymentMetrics,
        )
    }

    @Test
    fun `create payment returns initiated response and stores idempotency result`() {
        val result = service.createPayment(request(), "merchant_demo", "idem_001")

        assertThat(result.isNew).isTrue()
        assertThat(result.response.status).isEqualTo(PaymentStatus.INITIATED)
        verify(idempotencyService).storeResult(eq("merchant_demo"), eq("idem_001"), eq(request()), any())
        verify(paymentMetrics).recordPaymentCreated("CARD", "merchant_demo")
    }

    @Test
    fun `process payment transitions and calls provider`() {
        val payment = payment(status = PaymentStatus.INITIATED)
        whenever(paymentRepository.findById("pay_001")).thenReturn(Optional.of(payment))
        whenever(paymentLock.withPaymentLock(eq("pay_001"), any<() -> com.yuno.payment.api.v1.dto.PaymentResponse?>()))
            .thenAnswer { (it.arguments[1] as () -> Any?).invoke() }
        whenever(stateMachine.transition(eq(payment), eq(PaymentStatus.PROCESSING), any(), eq(null)))
            .thenAnswer { payment.apply { status = PaymentStatus.PROCESSING } }
        whenever(routingEngine.resolveProviderChain(eq(PaymentMethodType.CARD), any()))
            .thenReturn(listOf("PROVIDER_A"))
        whenever(retryCoordinator.executeWithFailover(any(), any()))
            .thenReturn(ProviderResult(PaymentStatus.CAPTURED, "PROVIDER_A", "txn_001"))
        whenever(stateMachine.transition(any(), eq(PaymentStatus.CAPTURED), anyOrNull(), any()))
            .thenAnswer { (it.arguments[0] as PaymentEntity).apply { status = PaymentStatus.CAPTURED } }

        val response = service.processPayment("pay_001")

        assertThat(response).isNotNull()
        verify(retryCoordinator).executeWithFailover(any(), any())
        verify(eventPublisher).publishEvent(any<PaymentStatusChangedApplicationEvent>())
    }

    private fun request() = CreatePaymentRequest(
        amount = MoneyRequest(1000, "INR"),
        paymentMethod = PaymentMethodRequest(
            type = PaymentMethodType.CARD,
            card = CardRequest("4111111111111111", "12", "2027", "123"),
        ),
    )

    private fun payment(status: PaymentStatus) = PaymentEntity(
        id = "pay_001",
        merchantId = "merchant_demo",
        idempotencyKey = "idem_001",
        status = status,
        paymentMethodType = PaymentMethodType.CARD,
        amountValue = 1000,
        amountCurrency = "INR",
    )
}
