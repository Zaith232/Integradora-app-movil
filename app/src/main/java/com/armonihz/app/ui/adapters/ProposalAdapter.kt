package com.armonihz.app.ui.adapters

import android.graphics.Paint // ⬅️ Nuevo
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
    private val onMusicianClick: (Int) -> Unit // ⬅️ Añadimos el listener para el nombre
) : RecyclerView.Adapter<ProposalAdapter.ProposalViewHolder>() {

    class ProposalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMusicianName: TextView = view.findViewById(R.id.tvMusicianName)
        val tvProposedPrice: TextView = view.findViewById(R.id.tvProposedPrice)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val btnAcceptProposal: Button = view.findViewById(R.id.btnAcceptProposal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProposalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_proposal, parent, false)
        return ProposalViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProposalViewHolder, position: Int) {
        val proposal = proposalsList[position]

        // Configuramos el nombre para que parezca un enlace (subrayado) y responda al clic
        holder.tvMusicianName.text = proposal.musician.stage_name
        holder.tvMusicianName.paintFlags = holder.tvMusicianName.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        holder.tvMusicianName.setOnClickListener {
            onMusicianClick(proposal.musician.id) // ⬅️ Enviamos el ID del músico
        }

        holder.tvProposedPrice.text = "Precio propuesto: $${proposal.proposed_price} MXN"
        holder.tvMessage.text = proposal.message ?: "Sin mensaje"

        when (proposal.status) {
            "accepted" -> {
                holder.btnAcceptProposal.text = "Propuesta Aceptada"
                holder.btnAcceptProposal.isEnabled = false
                holder.btnAcceptProposal.setBackgroundColor(holder.itemView.context.getColor(android.R.color.darker_gray))
                holder.btnAcceptProposal.visibility = View.VISIBLE
            }
            "rejected" -> {
                holder.btnAcceptProposal.visibility = View.GONE
            }
            else -> {
                holder.btnAcceptProposal.text = "Aceptar Propuesta"
                holder.btnAcceptProposal.isEnabled = true
                holder.btnAcceptProposal.visibility = View.VISIBLE

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