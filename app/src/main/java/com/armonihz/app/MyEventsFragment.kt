package com.armonihz.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.armonihz.app.databinding.FragmentMyEventsBinding

class MyEventsFragment : Fragment() {

    private var _binding: FragmentMyEventsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentMyEventsBinding.inflate(inflater, container, false)

        // ➕ FAB → Agregar evento
        binding.fabAddEvent.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AddEventFragment())
                .addToBackStack(null)
                .commit()
        }

        // 🏠 Home
        binding.btnHome.setOnClickListener {
            open(HomeFragment())
        }

        // ⭐ Favoritos
        binding.btnFavorite.setOnClickListener {
            open(FavoritesFragment())
        }

        // 👤 Perfil
        binding.btnProfile.setOnClickListener {
            open(UserProfileFragment())
        }

        // 📅 Eventos (ya estás aquí → no hace nada)
        binding.btnEvent.setOnClickListener { }

        return binding.root
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
