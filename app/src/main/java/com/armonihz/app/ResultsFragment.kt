package com.armonihz.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class ResultsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_results, container, false)

        // Enlazamos el nuevo BottomNavigationView
        val bottomNavigation = view.findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // Configuramos los clics de cada pestaña
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
                    open(UserProfileFragment())
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