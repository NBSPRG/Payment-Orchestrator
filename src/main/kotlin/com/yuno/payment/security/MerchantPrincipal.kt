package com.yuno.payment.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

data class MerchantPrincipal(
    val merchantId: String,
    val environment: String,
    val rateLimitTier: String? = "STANDARD",
    val allowedPaymentMethods: Set<String>? = null,
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_MERCHANT"))
    override fun getPassword(): String = ""
    override fun getUsername(): String = merchantId
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}
