package com.yuno.payment.persistence.repository

import com.yuno.payment.persistence.entity.MerchantEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MerchantRepository : JpaRepository<MerchantEntity, String>
