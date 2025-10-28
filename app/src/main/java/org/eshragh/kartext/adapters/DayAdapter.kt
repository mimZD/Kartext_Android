package org.eshragh.kartext.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.eshragh.kartext.R
import org.eshragh.kartext.models.DayRecord
import org.eshragh.kartext.utils.DateConverter

class DayAdapter(
    private val days: List<DayRecord>,
    private val onDayClick: (DayRecord) -> Unit
) : RecyclerView.Adapter<DayAdapter.DayViewHolder>() {

    class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDayNumber: TextView = itemView.findViewById(R.id.tvDayNumber)
        val tvDayOfWeek: TextView = itemView.findViewById(R.id.tvDayOfWeek)
        val tvDayTotalHours: TextView = itemView.findViewById(R.id.tvDayTotalHours)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_day_record, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = days[position]
        holder.tvDayNumber.text = DateConverter.toPersianNumbers(day.dayOfMonth.toString())
        holder.tvDayOfWeek.text = day.dayOfWeek
        val durationText = "${DateConverter.toPersianNumbers(day.totalHours.toString())} ساعت و ${DateConverter.toPersianNumbers(day.totalMinutes.toString())} دقیقه"
        holder.tvDayTotalHours.text = "مجموع: $durationText"

        holder.itemView.setOnClickListener { onDayClick(day) }
    }

    override fun getItemCount(): Int {
        return days.size
    }
}