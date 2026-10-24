package br.com.bloqueiaads.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterRulesTest {
    @Test fun blocksExactAdvertisingDomain() { assertTrue(FilterRules.isBlocked("doubleclick.net")) }
    @Test fun blocksAdvertisingSubdomain() { assertTrue(FilterRules.isBlocked("ad.example.doubleclick.net")) }
    @Test fun doesNotBlockLookalikeDomain() { assertFalse(FilterRules.isBlocked("notdoubleclick.net")) }
    @Test fun allowsOrdinaryDomain() { assertFalse(FilterRules.isBlocked("www.wikipedia.org")) }
    @Test fun ignoresCaseAndTrailingDot() { assertTrue(FilterRules.isBlocked("ADS.GOOGLESYNDICATION.COM.")) }
}
