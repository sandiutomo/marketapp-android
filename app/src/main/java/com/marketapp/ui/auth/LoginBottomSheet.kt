package com.marketapp.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.marketapp.R
import com.marketapp.databinding.BottomSheetLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by activityViewModels()

    private var isRegisterMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = BottomSheetLoginBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateMode()
        binding.btnGoogle.setOnClickListener { launchGoogleSignIn() }
        binding.btnPrimary.setOnClickListener { onPrimaryAction() }
        binding.tvToggle.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateMode()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressBar.isVisible = state is AuthUiState.Loading
                        if (state is AuthUiState.Error) showError(state.message)
                    }
                }
                launch {
                    viewModel.authState.collect { user ->
                        if (user != null) dismiss()
                    }
                }
            }
        }
    }

    private fun updateMode() {
        if (isRegisterMode) {
            binding.tvTitle.setText(R.string.create_account)
            binding.tilName.isVisible = true
            binding.btnPrimary.setText(R.string.register)
            binding.tvToggle.setText(R.string.already_have_account)
        } else {
            binding.tvTitle.setText(R.string.sign_in)
            binding.tilName.isVisible = false
            binding.btnPrimary.setText(R.string.sign_in)
            binding.tvToggle.setText(R.string.no_account)
        }
    }

    private fun launchGoogleSignIn() {
        val credentialManager = CredentialManager.create(requireContext())
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .build()
            )
            .build()
        lifecycleScope.launch {
            runCatching {
                val result = credentialManager.getCredential(requireActivity(), request)
                GoogleIdTokenCredential.createFrom(result.credential.data).idToken
            }.onSuccess { idToken ->
                viewModel.signInWithGoogle(idToken)
            }.onFailure { e ->
                if (e !is CancellationException) showError(e.message ?: getString(R.string.error_generic))
            }
        }
    }

    private fun onPrimaryAction() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        if (email.isEmpty() || password.isEmpty()) return

        if (isRegisterMode) {
            val name = binding.etName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return
            viewModel.registerWithEmail(email, password, name)
        } else {
            viewModel.signInWithEmail(email, password)
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LoginBottomSheet"
    }
}
