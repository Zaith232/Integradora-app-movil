package com.armonihz.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val cardMariachi = view.findViewById<CardView>(R.id.cardMariachi)
        cardMariachi.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MusicianProfileFragment())
                .addToBackStack(null)
                .commit()
        }
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        searchInput.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ResultsFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<Button>(R.id.btnHome).setOnClickListener { }
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
