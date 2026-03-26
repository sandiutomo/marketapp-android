package com.marketapp.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.marketapp.databinding.BottomSheetEditNameBinding
import com.marketapp.ui.auth.AuthViewModel
import com.marketapp.ui.auth.UpdateNameState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditNameBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "EditNameBottomSheet"
        const val REQUEST_KEY = "edit_name_result"
        const val RESULT_NAME = "updated_name"

        fun newInstance(currentName: String) = EditNameBottomSheet().apply {
            arguments = bundleOf("name" to currentName)
        }
    }

    private var _binding: BottomSheetEditNameBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = BottomSheetEditNameBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etName.setText(arguments?.getString("name"))

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text?.toString()?.trim() ?: return@setOnClickListener
            if (name.isEmpty()) {
                showError(getString(com.marketapp.R.string.error_name_empty))
                return@setOnClickListener
            }
            viewModel.updateName(name)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.updateNameState.collect { state ->
                    binding.progressBar.isVisible = state is UpdateNameState.Loading
                    binding.btnSave.isEnabled = state !is UpdateNameState.Loading
                    when (state) {
                        is UpdateNameState.Success -> {
                            val name = binding.etName.text?.toString()?.trim().orEmpty()
                            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_NAME to name))
                            viewModel.resetUpdateNameState()
                            dismiss()
                        }
                        is UpdateNameState.Error -> showError(state.message)
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
