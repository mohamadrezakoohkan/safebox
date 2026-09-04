import Foundation
import Testing
@testable import SafeBox

/// The iteration-3 §7 string table: the renames landed with their text
/// verbatim, the retired IDs are gone, and every new ID really is in the
/// compiled catalog rather than only falling back to its in-code default.
struct DisguiseStringsTests {
    private let missing = "<missing>"

    private func catalog(_ key: String) -> String {
        Bundle.main.localizedString(forKey: key, value: missing, table: nil)
    }

    // MARK: - Renames (text verbatim)

    @Test func renamedGuideKeysKeptTheirText() {
        #expect(catalog("calculator_guide_page3_title") == "Your code is a key sequence")
        #expect(catalog("calculator_guide_page3_body")
                == "Pick 4 to 32 calculator keys — digits and symbols all count, and order matters. Try one here. This is just practice; nothing is saved.")
        #expect(catalog("calculator_guide_try") == "Tap at least 4 keys")
        #expect(catalog("calculator_guide_ok") == "That would work — symbols make it stronger")
        #expect(catalog("calculator_guide_page4_title") == "Press = to enter")
        #expect(catalog("calculator_guide_page4_body")
                == "To unlock, type your code on the calculator and press =. A wrong code just calculates — no error, no hint that anything is hidden.")
    }

    @Test func theOldGuideKeysAreGone() {
        for key in ["onboarding_page3_title", "onboarding_page3_body", "onboarding_page3_try",
                    "onboarding_page3_ok", "onboarding_page4_title", "onboarding_page4_body"] {
            #expect(catalog(key) == missing, "\(key) should have been renamed away")
        }
    }

    // MARK: - Retired

    @Test func thePageOneKeysAreRetired() {
        #expect(catalog("onboarding_page1_title") == missing)
        #expect(catalog("onboarding_page1_body") == missing)
    }

    // MARK: - Survivors

    @Test func theSharedGuideKeysSurvived() {
        #expect(catalog("onboarding_page3_clear") == "Reset")
        #expect(catalog("onboarding_page4_warning")
                == "There is no recovery. If you forget your code, the vault stays locked forever.")
        #expect(catalog("onboarding_page2_title") == "Secretly, it's your vault")
        #expect(catalog("onboarding_skip") == "Skip")
        #expect(catalog("settings_change_title") == "Change passcode")
    }

    // MARK: - New IDs

    @Test func everyFaceCardStringResolves() {
        #expect(catalog("calculator_display_name") == "Calculator")
        #expect(catalog("numpad_display_name") == "PIN pad")
        #expect(catalog("pattern_display_name") == "Pattern")
        #expect(catalog("calculator_commit_gesture") == "the = key")
        #expect(catalog("numpad_commit_gesture") == "the ✓ key")
        #expect(catalog("pattern_commit_gesture") == "a finger lift")
        #expect(catalog("pattern_a11y_note") == "Not usable with a screen reader")
        #expect(catalog("disguise_grade_native") == "Matches the app's name and icon")
        #expect(catalog("disguise_grade_incoherent") == "Doesn't match the app's name and icon")
        #expect(catalog("disguise_current_badge") == "Current")
        for key in ["calculator_tagline", "numpad_tagline", "pattern_tagline",
                    "disguise_identity_disclosure"] {
            #expect(catalog(key) != missing, "\(key) is not in the catalog")
        }
    }

    @Test func everyFaceCaptionResolves() {
        var keys: [String] = ["numpad_face_title", "pattern_face_title"]
        for prefix in ["numpad", "pattern"] {
            keys += ["\(prefix)_prompt_new", "\(prefix)_prompt_new_change", "\(prefix)_hint",
                     "\(prefix)_too_short", "\(prefix)_prompt_confirm",
                     "\(prefix)_prompt_confirm_change", "\(prefix)_mismatch",
                     "\(prefix)_prompt_current", "\(prefix)_wrong_code",
                     "\(prefix)_guide_page3_title", "\(prefix)_guide_page3_body",
                     "\(prefix)_guide_try", "\(prefix)_guide_ok",
                     "\(prefix)_guide_page4_title", "\(prefix)_guide_page4_body"]
        }
        // TOO_LONG and TRIVIAL_WARNING exist only for the PIN pad.
        keys += ["numpad_too_long", "numpad_trivial_warning"]
        for key in keys {
            #expect(catalog(key) != missing, "\(key) is not in the catalog")
        }
    }

    @Test func theSwitchFlowStringsResolve() {
        #expect(catalog("settings_change_disguise_title") == "Change disguise")
        #expect(catalog("disguise_pick_action") == "Use this disguise")
        #expect(catalog("disguise_switch_success_title") == "Disguise changed")
        #expect(catalog("disguise_switch_success_body") != missing)
        #expect(catalog("onboarding_disguise_title") == "Pick a disguise")
        #expect(catalog("onboarding_disguise_body") != missing)
        #expect(catalog("onboarding_disguise_revisit_hint") != missing)
    }

    @Test func theExplainerTakesTwoStringArguments() {
        #expect(catalog("disguise_switch_explainer").hasPrefix(
            "Your current code belongs to the %1$@ disguise and is confirmed with %2$@."
        ))
        let rendered = VaultCopy.disguiseSwitchExplainer(currentName: "Calculator",
                                                         currentGesture: "the = key")
        #expect(rendered.hasPrefix("Your current code belongs to the Calculator disguise and is confirmed with the = key."))
    }

    /// The unreachable pattern kinds deliberately have no string of their own.
    @Test func thePatternHasNoTooLongOrTrivialString() {
        #expect(catalog("pattern_too_long") == missing)
        #expect(catalog("pattern_trivial_warning") == missing)
    }
}
