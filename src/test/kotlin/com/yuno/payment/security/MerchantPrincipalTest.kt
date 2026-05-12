package com.yuno.payment.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MerchantPrincipalTest {
    @Test
    fun `principal exposes merchant authority and account flags`() {
        val principal = MerchantPrincipal("merchant_demo", "TEST", "STANDARD")

        assertThat(principal.username).isEqualTo("merchant_demo")
        assertThat(principal.authorities.map { it.authority }).contains("ROLE_MERCHANT")
        assertThat(principal.isAccountNonExpired).isTrue()
        assertThat(principal.isAccountNonLocked).isTrue()
        assertThat(principal.isCredentialsNonExpired).isTrue()
        assertThat(principal.isEnabled).isTrue()
    }
}
