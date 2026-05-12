package com.yuno.payment.persistence.repository

import com.yuno.payment.persistence.entity.MerchantApiKeyEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MerchantApiKeyRepository : JpaRepository<MerchantApiKeyEntity, String> {
    fun findByKeyPrefixAndStatus(keyPrefix: String, status: String): MerchantApiKeyEntity?
}
