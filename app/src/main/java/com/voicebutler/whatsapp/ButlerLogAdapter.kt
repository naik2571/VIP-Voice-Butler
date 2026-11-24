package com.voicebutler.whatsapp

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class ButlerLogAdapter(
    private var logs: List<ButlerLogItem>
) : RecyclerView.Adapter<ButlerLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender: TextView = view.findViewById(R.id.tv_log_sender)
        val tvTime: TextView = view.findViewById(R.id.tv_log_time)
        val tvMessage: TextView = view.findViewById(R.id.tv_log_message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_butler_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = logs[position]
        holder.tvSender.text = item.sender
        holder.tvMessage.text = item.messageText

        val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
        holder.tvTime.text = DateFormat.format("hh:mm a", cal).toString()
    }

    override fun getItemCount(): Int = logs.size

    fun updateLogs(newLogs: List<ButlerLogItem>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}
