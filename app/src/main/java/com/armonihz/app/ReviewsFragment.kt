package com.armonihz.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class ReviewsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_reviews, container, false)

        val bottomNavigation = view.findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // Asumiendo que las reseñas pertenecen a la sección de "Perfil"
        bottomNavigation.selectedItemId = R.id.nav_profile

        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    open(HomeFragment())
                    true
                }
                R.id.nav_events -> {
                    open(MyEventsFragment())
                    true
                }
                R.id.nav_favorites -> {
                    open(FavoritesFragment())
                    true
                }
                R.id.nav_profile -> {
                    // Si ya estamos en el flujo de perfil
                    true
                }
                else -> false
            }
        }

        return view
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}