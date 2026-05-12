package com.yuno.payment.webhook

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface WebhookDeliveryRepository : JpaRepository<WebhookDeliveryEntity, Long> {
    fun findByStatusAndNextRetryAtBefore(status: String, cutoff: Instant): List<WebhookDeliveryEntity>
}
