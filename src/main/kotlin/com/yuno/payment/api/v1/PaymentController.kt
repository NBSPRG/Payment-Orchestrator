package com.yuno.payment.api.v1

import com.yuno.payment.api.v1.dto.CreatePaymentRequest
import com.yuno.payment.api.v1.dto.PaymentResponse
import com.yuno.payment.api.v1.dto.PaymentStatusResponse
import com.yuno.payment.api.v1.mapper.toResponse
import com.yuno.payment.domain.exception.PaymentNotFoundException
import com.yuno.payment.domain.model.PaymentStatus
import com.yuno.payment.orchestration.PaymentOrchestrationService
import com.yuno.payment.persistence.repository.PaymentRepository
import com.yuno.payment.security.MerchantPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.validation.annotation.Validated

@RestController
@Validated
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val orchestrationService: PaymentOrchestrationService,
    private val paymentRepository: PaymentRepository,
) {
    @PostMapping
    @Operation(summary = "Create a payment", description = "Creates an INITIATED payment. Provider processing is asynchronous.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Payment created"),
            ApiResponse(responseCode = "200", description = "Idempotent replay"),
            ApiResponse(responseCode = "400", description = "Validation failed"),
            ApiResponse(responseCode = "401", description = "Missing or invalid API key"),
            ApiResponse(responseCode = "409", description = "Idempotency or concurrent processing conflict"),
        ],
    )
    fun createPayment(
        @AuthenticationPrincipal principal: MerchantPrincipal,
        @RequestHeader("X-Idempotency-Key")
        @Size(min = 8, max = 128, message = "idempotency key must be 8 to 128 characters")
        @Pattern(
            regexp = "^[A-Za-z0-9._:-]+$",
            message = "idempotency key may contain letters, numbers, dot, underscore, colon, and hyphen",
        )
        idempotencyKey: String,
        @Valid @RequestBody request: CreatePaymentRequest,
    ): ResponseEntity<PaymentResponse> {
        val (response, isNew) = orchestrationService.createPayment(request, principal.merchantId, idempotencyKey)
        return if (isNew) {
            ResponseEntity.status(HttpStatus.CREATED).body(response)
        } else {
            ResponseEntity.ok(response)
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Payment found"),
            ApiResponse(responseCode = "404", description = "Payment not found"),
        ],
    )
    fun getPayment(
        @AuthenticationPrincipal principal: MerchantPrincipal,
        @PathVariable id: String,
    ): PaymentResponse =
        paymentRepository.findByIdAndMerchantId(id, principal.merchantId)?.toResponse()
            ?: throw PaymentNotFoundException(id)

    @GetMapping("/{id}/status")
    @Operation(summary = "Get payment status")
    fun getPaymentStatus(
        @AuthenticationPrincipal principal: MerchantPrincipal,
        @PathVariable id: String,
    ): PaymentStatusResponse {
        val payment = paymentRepository.findByIdAndMerchantId(id, principal.merchantId)
            ?: throw PaymentNotFoundException(id)
        return PaymentStatusResponse(
            paymentId = payment.id,
            status = payment.status,
            updatedAt = payment.updatedAt,
        )
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel payment")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Payment cancelled"),
            ApiResponse(responseCode = "409", description = "Payment is already being processed"),
            ApiResponse(responseCode = "422", description = "Invalid state transition"),
        ],
    )
    fun cancelPayment(
        @AuthenticationPrincipal principal: MerchantPrincipal,
        @PathVariable id: String,
    ): PaymentResponse =
        orchestrationService.cancelPayment(id, principal.merchantId)

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund payment")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Payment refunded"),
            ApiResponse(responseCode = "409", description = "Payment is already being processed"),
            ApiResponse(responseCode = "422", description = "Invalid state transition"),
        ],
    )
    fun refundPayment(
        @AuthenticationPrincipal principal: MerchantPrincipal,
        @PathVariable id: String,
    ): PaymentResponse =
        orchestrationService.refundPayment(id, principal.merchantId)

    @GetMapping
    @Operation(summary = "List payments")
    fun listPayments(
        @AuthenticationPrincipal principal: MerchantPrincipal,
        @RequestParam(required = false) status: PaymentStatus?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): Page<PaymentResponse> {
        val page = if (status != null) {
            paymentRepository.findByMerchantIdAndStatus(principal.merchantId, status, pageable)
        } else {
            paymentRepository.findByMerchantId(principal.merchantId, pageable)
        }
        return page.map { it.toResponse() }
    }
}
