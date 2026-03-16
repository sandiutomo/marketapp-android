package com.marketapp.config

/**
 * Braze Feature Flags used in the app. Adding a new flag here is the only step needed to make it:
 *  - readable via Braze.getInstance(context).getFeatureFlag(flag.key)
 *  - visible in the debug panel (DebugInfoBottomSheet)
 *
 * SETUP: Create the matching flag ID in Braze Console (Feature Flags → Create)
 * before deploying. Flags not created in Braze return null (shown as "unset").
 *
 * Note: Braze Feature Flags is a paid add-on. Verify it is enabled on your plan.
 */
enum class BrazeFlag(val key: String, val label: String) {
    // Overrides the "Place Order" button label in PaymentFragment.
    // enabled=true: reads string property "label" from the Braze flag and applies it.
    // enabled=false / unset: default button text ("Place Order") is used.
    // Useful for personalizing CTA copy per Braze user segment (e.g. VIP, first-time buyer).
    CHECKOUT_CTA ("checkout-cta", "Checkout CTA Label"),
    ;
}