package sy.medpro.watani.integration

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import sy.medpro.watani.data.ReferralClaimReply
import sy.medpro.watani.data.ReferralGateway
import sy.medpro.watani.data.ReferralMe
import sy.medpro.watani.data.ReferralMeReply
import sy.medpro.watani.data.ReferralReward
import sy.medpro.watani.data.parseReferralMe
import sy.medpro.watani.data.referralClaimErrorMessage
import sy.medpro.watani.ui.referral.ReferralRoute

/**
 * Layer 3 — نظام إحالة الطلاب (build 1.2.54).
 *
 * Two halves: (1) the /v1/referral/me parser + error mapping the
 * whole feature stands on (pure, exact payload shapes as the
 * gateway emits them); (2) the real invite screen driven by a
 * fake gateway through the full claim journey: load → code +
 * «فعّل عن طريقك 3 من 7» (ACTIVATED count, the doctor's
 * corrected spec) → enter a referrer code → success → the claim
 * card disappears once the server reports the attribution.
 * The gateway-side counting journey itself (registration does
 * not count, paid activation counts, the 7th issues a usable
 * reward, reward activations never count) is pinned by
 * watani-api/test_referrals.mjs against the shipped pure core.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReferralIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // ─── Parser ───

    private fun fullPayload(): JSONObject = JSONObject(
        """
        {
          "ok": true,
          "code": "MPAB3DEF",
          "count": 3,
          "registeredCount": 5,
          "threshold": 7,
          "remainingForNextReward": 4,
          "rewards": [
            {"milestone": 1, "code": "RWDAB3DEF456", "expiresAt": 1900000000000, "createdAt": 1800000000000}
          ],
          "referredBy": {"code": "MPXYZ999", "referrerName": "صديق", "createdAt": 1790000000000},
          "canClaim": false,
          "claimDeadline": 1792592000000,
          "activation": {"activated": true, "expiresAt": 1900000000000}
        }
        """.trimIndent()
    )

    @Test
    fun parserReadsTheFullGatewayPayload() {
        val me = parseReferralMe(fullPayload())
        requireNotNull(me)
        assertEquals("MPAB3DEF", me.code)
        assertEquals("MP-AB3DEF", me.displayCode)
        assertEquals(3, me.count)
        assertEquals(5, me.registeredCount)
        assertEquals(7, me.threshold)
        assertEquals(4, me.remainingForNextReward)
        assertEquals(1, me.rewards.size)
        assertEquals(1, me.rewards[0].milestone)
        assertEquals(1900000000000L, me.rewards[0].expiresAt)
        assertEquals("MPXYZ999", me.referredByCode)
        assertEquals("صديق", me.referredByName)
        assertTrue(!me.canClaim)
        assertTrue(me.activationActivated)
        assertEquals(1900000000000L, me.activationExpiresAt)
    }

    @Test
    fun parserRejectsNonOkAndBlankCodePayloads() {
        assertNull(parseReferralMe(JSONObject("""{"ok": false, "code": "MPAB3DEF"}""")))
        assertNull(parseReferralMe(JSONObject("""{"ok": true, "code": "  "}""")))
        val minimal = parseReferralMe(JSONObject("""{"ok": true, "code": "MPZZZZZZ"}"""))
        requireNotNull(minimal)
        assertEquals(0, minimal.count)
        assertEquals(7, minimal.threshold)
        assertTrue(minimal.rewards.isEmpty())
        assertNull(minimal.referredByCode)
    }

    @Test
    fun claimErrorMappingCoversEveryGatewayError() {
        assertTrue(referralClaimErrorMessage("invalid_code").contains("غير صحيح"))
        assertTrue(referralClaimErrorMessage("self_referral").contains("رمزك"))
        assertTrue(referralClaimErrorMessage("already_claimed").contains("سبق"))
        assertTrue(referralClaimErrorMessage("account_too_old").contains("الجديدة"))
        assertTrue(referralClaimErrorMessage("auth").contains("سجّل الدخول"))
        assertTrue(referralClaimErrorMessage("weird").contains("تعذّر"))
        assertTrue(referralClaimErrorMessage(null).contains("تعذّر"))
    }

    // ─── Screen journey with a fake gateway ───

    private class FakeReferralGateway : ReferralGateway {
        var claimedWith: String? = null
        var attributed = false

        override suspend fun fetchMine(): ReferralMeReply {
            val me = ReferralMe(
                code = "MPAB3DEF",
                count = 3,
                registeredCount = 5,
                threshold = 7,
                remainingForNextReward = 4,
                rewards = emptyList(),
                referredByCode = if (attributed) "MPXYZ999" else null,
                referredByName = if (attributed) "صديق" else null,
                canClaim = !attributed,
                activationActivated = false,
                activationExpiresAt = 0L
            )
            return ReferralMeReply(200, me)
        }

        override suspend fun claim(code: String): ReferralClaimReply {
            claimedWith = code
            attributed = true
            return ReferralClaimReply(200)
        }
    }

    @Test
    fun inviteScreenShowsCodeActivatedCounterAndClaimsOnce() {
        val fake = FakeReferralGateway()
        var backs = 0
        composeRule.setContent {
            ReferralRoute(onBack = { backs++ }, gateway = fake)
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasText("MP-AB3DEF", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("MP-AB3DEF").assertIsDisplayed()
        // The headline counter is the ACTIVATED count (3), with the
        // registered-but-not-activated remainder explained below it.
        composeRule.onNodeWithText("فعّل عن طريقك 3 من 7").assertIsDisplayed()
        composeRule.onNodeWithText("بقي 4 تفعيلًا وتصلك مكافأة التفعيل المجاني", substring = true)
            .assertIsDisplayed()

        // Claim journey: type the referrer's code and submit.
        composeRule.onNode(hasSetTextAction()).performTextInput("MP-XYZ999")
        composeRule.onNodeWithText("تسجيل الرمز").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            fake.claimedWith != null &&
                composeRule.onAllNodes(hasText("سجّلت برمز إحالة", substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("MP-XYZ999", fake.claimedWith)
        // After the attribution lands, the claim card is gone for good.
        composeRule.onAllNodes(hasText("عندك رمز إحالة من صديق؟", substring = true))
            .assertCountEquals(0)
        assertEquals(0, backs)
    }

    @Test
    fun inviteScreenShowsEarnedRewardCard() {
        val fake = object : ReferralGateway {
            override suspend fun fetchMine(): ReferralMeReply = ReferralMeReply(
                200,
                ReferralMe(
                    code = "MPAB3DEF",
                    count = 7,
                    registeredCount = 7,
                    threshold = 7,
                    remainingForNextReward = 7,
                    rewards = listOf(
                        ReferralReward(1, "RWDAB3DEF456", 1_900_000_000_000L, 1_800_000_000_000L)
                    ),
                    referredByCode = null,
                    referredByName = null,
                    canClaim = false,
                    activationActivated = true,
                    activationExpiresAt = 1_900_000_000_000L
                )
            )

            override suspend fun claim(code: String): ReferralClaimReply = ReferralClaimReply(400, "already_claimed")
        }
        composeRule.setContent { ReferralRoute(onBack = {}, gateway = fake) }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(hasText("وصلتك مكافأة إحالة", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("وصلتك مكافأة إحالة: تفعيل مجاني", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("فعّل عن طريقك 7 من 7").assertIsDisplayed()
    }
}
