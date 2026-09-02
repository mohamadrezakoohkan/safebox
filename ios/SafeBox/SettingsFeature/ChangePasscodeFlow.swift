import SwiftUI

/// Hosts the calculator surface driven by a PasscodeEntrySession:
/// VerifyCurrent → EnterNew → Confirm, with visible wrong-current feedback and
/// a Cancel button in every phase. Same component as the lock screen, in a
/// different mode.
struct ChangePasscodeFlow: View {
    @State private var session: PasscodeEntrySession
    @State private var viewModel: CalculatorViewModel
    let onChanged: () -> Void
    @Environment(\.dismiss) private var dismiss

    init(session: PasscodeEntrySession, onChanged: @escaping () -> Void) {
        _session = State(initialValue: session)
        self.onChanged = onChanged
        let vm = CalculatorViewModel(onCommit: { keys, overflowed in
            Task { await session.commit(sequence: keys, overflowed: overflowed) }
        })
        vm.onKeyPress = { session.keyPressed() }
        _viewModel = State(initialValue: vm)
    }

    var body: some View {
        NavigationStack {
            CalculatorSurface(
                display: viewModel.display,
                banner: session.banner,
                bannerIsError: session.bannerIsError,
                shakeToken: session.shakeToken,
                clearLabel: viewModel.clearLabel,
                ringOperator: viewModel.ringOperator,
                onKey: { viewModel.press($0) }
            )
            .navigationTitle(LockCopy.settingsChangeTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(LockCopy.changeCancel) { dismiss() }
                }
            }
        }
        .onChange(of: session.phase) { _, phase in
            if phase == .done {
                onChanged()
                dismiss()
            }
        }
    }
}
