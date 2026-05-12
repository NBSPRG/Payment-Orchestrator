package com.yuno.payment.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "merchants")
class MerchantEntity(
    @Id
    @Column(name = "id")
    var id: String = "",

    @Column(name = "name")
    var name: String = "",

    @Column(name = "status")
    var status: String = "",

    @Column(name = "environment")
    var environment: String = "",

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now(),

    @Column(name = "webhook_secret")
    var webhookSecret: String? = null,
)
