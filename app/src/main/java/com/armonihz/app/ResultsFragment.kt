package com.armonihz.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class ResultsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_results, container, false)

        view.findViewById<Button>(R.id.btnHome).setOnClickListener {
            open(HomeFragment())
        }
        view.findViewById<Button>(R.id.btnFavorite).setOnClickListener {
            open(FavoritesFragment())
        }
        view.findViewById<Button>(R.id.btnProfile).setOnClickListener {
            open(UserProfileFragment())
        }
        view.findViewById<Button>(R.id.btnEvent).setOnClickListener {
            open(MyEventsFragment())
        }

        return view
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
