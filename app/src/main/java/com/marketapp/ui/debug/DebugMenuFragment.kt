package com.marketapp.ui.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.marketapp.R
import com.marketapp.databinding.FragmentDebugMenuBinding
import com.statsig.androidsdk.Statsig

class DebugMenuFragment : Fragment() {

    private var _binding: FragmentDebugMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentDebugMenuBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowStatsigDebug.apply {
            ivIcon.setImageResource(R.drawable.ic_settings)
            tvLabel.text = "Statsig Debug View"
            root.setOnClickListener {
                Statsig.openDebugView(requireActivity()) { /* reload not needed */ }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
