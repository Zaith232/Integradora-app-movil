package com.armonihz.app.ui.adapters

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.ApplicationItem

class ProposalAdapter(
    private var proposalsList: List<ApplicationItem>,
    private val onAcceptClick: (Int) -> Unit,
    private val onCancelClick: (Int) -> Unit,
    private val onMusicianClick: (Int) -> Unit
) : RecyclerView.Adapter<ProposalAdapter.ProposalViewHolder>() {

    class ProposalViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val tvMusicianName: TextView = view.findViewById(R.id.tvMusicianName)
        val tvProposedPrice: TextView = view.findViewById(R.id.tvProposedPrice)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)

        val btnAcceptProposal: Button = view.findViewById(R.id.btnAcceptProposal)
        val btnCancelProposal: Button = view.findViewById(R.id.btnCancelProposal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProposalViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_proposal, parent, false)

        return ProposalViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProposalViewHolder, position: Int) {

        val proposal = proposalsList[position]

        holder.tvMusicianName.text = proposal.musician.stage_name
        holder.tvMusicianName.paintFlags =
            holder.tvMusicianName.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        holder.tvMusicianName.setOnClickListener {
            onMusicianClick(proposal.musician.id)
        }

        holder.tvProposedPrice.text = "Precio propuesto: $${proposal.proposed_price} MXN"
        holder.tvMessage.text = proposal.message ?: "Sin mensaje"

        when (proposal.status) {

            "accepted" -> {

                // Ocultar aceptar
                holder.btnAcceptProposal.visibility = View.GONE

                // Mostrar cancelar
                holder.btnCancelProposal.visibility = View.VISIBLE

                holder.btnCancelProposal.setOnClickListener {
                    onCancelClick(proposal.id)
                }
            }

            "rejected" -> {

                holder.btnAcceptProposal.visibility = View.GONE
                holder.btnCancelProposal.visibility = View.GONE
            }

            else -> {

                // Estado pendiente
                holder.btnAcceptProposal.visibility = View.VISIBLE
                holder.btnCancelProposal.visibility = View.GONE

                holder.btnAcceptProposal.setOnClickListener {
                    onAcceptClick(proposal.id)
                }
            }
        }
    }

    override fun getItemCount() = proposalsList.size

    fun updateData(newProposals: List<ApplicationItem>) {
        proposalsList = newProposals
        notifyDataSetChanged()
    }
}