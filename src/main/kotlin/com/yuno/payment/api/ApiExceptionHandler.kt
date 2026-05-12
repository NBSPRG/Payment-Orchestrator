package com.yuno.payment.api

import com.yuno.payment.api.v1.dto.ErrorResponse
import com.yuno.payment.domain.exception.ConcurrentPaymentProcessingException
import com.yuno.payment.domain.exception.IdempotencyConflictException
import com.yuno.payment.domain.exception.InvalidStateTransitionException
import com.yuno.payment.domain.exception.PaymentNotFoundException
import io.micrometer.tracing.Tracer
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import jakarta.validation.ConstraintViolationException

@RestControllerAdvice
class ApiExceptionHandler(
    private val tracer: Tracer?,
) {
    @ExceptionHandler(PaymentNotFoundException::class)
    fun notFound(ex: PaymentNotFoundException): ResponseEntity<ErrorResponse> =
        buildResponse(HttpStatus.NOT_FOUND, "Payment not found", ex.message, "payment_not_found")

    @ExceptionHandler(IdempotencyConflictException::class)
    fun conflict(ex: IdempotencyConflictException): ResponseEntity<ErrorResponse> =
        buildResponse(HttpStatus.CONFLICT, "Idempotency conflict", ex.message, "idempotency_conflict")

    @ExceptionHandler(InvalidStateTransitionException::class)
    fun invalidState(ex: InvalidStateTransitionException): ResponseEntity<ErrorResponse> =
        buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid state transition", ex.message, "invalid_state_transition")

    @ExceptionHandler(ConcurrentPaymentProcessingException::class)
    fun concurrentProcessing(ex: ConcurrentPaymentProcessingException): ResponseEntity<ErrorResponse> =
        buildResponse(HttpStatus.CONFLICT, "Concurrent processing", ex.message, "concurrent_processing")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val detail = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Request validation failed"
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", detail, "validation_failed")
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun constraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val detail = ex.constraintViolations.firstOrNull()?.message ?: "Request validation failed"
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", detail, "validation_failed")
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        buildResponse(HttpStatus.BAD_REQUEST, "Invalid request", ex.message, "invalid_request")

    private fun buildResponse(
        status: HttpStatus,
        title: String,
        detail: String?,
        errorCode: String,
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(status).body(
        ErrorResponse(
            type = "https://api.yuno.com/errors/$errorCode",
            title = title,
            status = status.value(),
            detail = detail ?: title,
            errorCode = errorCode,
            traceId = tracer?.currentSpan()?.context()?.traceId(),
            timestamp = Instant.now().toString(),
        ),
    )
}
