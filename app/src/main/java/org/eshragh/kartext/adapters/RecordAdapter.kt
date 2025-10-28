package org.eshragh.kartext.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.Group
import androidx.recyclerview.widget.RecyclerView
import org.eshragh.kartext.R
import org.eshragh.kartext.models.Record
import org.eshragh.kartext.models.RecordType
import org.eshragh.kartext.utils.DateConverter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class RecordAdapter(
    private val records: List<Record>,
    private val onEnterTimeClick: (Record) -> Unit,
    private val onExitTimeClick: (Record) -> Unit,
    private val onDeductionsClick: (Record) -> Unit
) : RecyclerView.Adapter<RecordAdapter.RecordViewHolder>() {

    class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvEnterTime: TextView = itemView.findViewById(R.id.tvEnterTime)
        val tvExitTime: TextView = itemView.findViewById(R.id.tvExitTime)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val tvDeductions: TextView = itemView.findViewById(R.id.tvDeductions)
        val deductionsGroup: Group = itemView.findViewById(R.id.deductions_group)
        val enterTimeLayout: LinearLayout = itemView.findViewById(R.id.enter_time_layout)
        val exitTimeLayout: LinearLayout = itemView.findViewById(R.id.exit_time_layout)
        val deductionsLayout: LinearLayout = itemView.findViewById(R.id.deductions_layout)
        val leaveTypeLabel: TextView = itemView.findViewById(R.id.tv_leave_type_label)
        val cardView: CardView = itemView.findViewById(R.id.card_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val record = records[position]
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        holder.tvEnterTime.text = DateConverter.toPersianNumbers(timeFormat.format(record.enterTime))

        if (record.exitTime != null) {
            holder.tvExitTime.text = DateConverter.toPersianNumbers(timeFormat.format(record.exitTime!!))
            holder.deductionsGroup.visibility = View.VISIBLE

            val deductionsMinutes = TimeUnit.MILLISECONDS.toMinutes(record.deductions)
            holder.tvDeductions.text = DateConverter.toPersianNumbers(String.format("%d دقیقه", deductionsMinutes))

            val durationMillis = (record.exitTime!! - record.enterTime) - record.deductions
            val hours = TimeUnit.MILLISECONDS.toHours(durationMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60
            
            val durationText = if (hours > 0) {
                DateConverter.toPersianNumbers(String.format("%d ساعت و %d دقیقه", hours, minutes))
            } else {
                DateConverter.toPersianNumbers(String.format("%d دقیقه", minutes))
            }
            holder.tvDuration.text = durationText
        } else {
            holder.tvExitTime.text = "--:--"
            holder.deductionsGroup.visibility = View.GONE
            holder.tvDuration.text = ""
        }

        // Handle leave types
        if (record.type != RecordType.WORK) {
            holder.leaveTypeLabel.visibility = View.VISIBLE
            holder.cardView.setCardBackgroundColor(Color.parseColor("#E3F2FD")) // Light blue background for leave
            holder.enterTimeLayout.isClickable = false
            holder.exitTimeLayout.isClickable = false
            holder.deductionsLayout.isClickable = false

            when (record.type) {
                RecordType.HOURLY_LEAVE -> holder.leaveTypeLabel.text = "مرخصی ساعتی"
                RecordType.DAILY_LEAVE -> holder.leaveTypeLabel.text = "مرخصی روزانه"
                else -> holder.leaveTypeLabel.visibility = View.GONE
            }
        } else {
            holder.leaveTypeLabel.visibility = View.GONE
            holder.cardView.setCardBackgroundColor(Color.WHITE)
            holder.enterTimeLayout.isClickable = true
            holder.exitTimeLayout.isClickable = true
            holder.deductionsLayout.isClickable = true

            holder.enterTimeLayout.setOnClickListener { onEnterTimeClick(record) }
            holder.exitTimeLayout.setOnClickListener { onExitTimeClick(record) }
            holder.deductionsLayout.setOnClickListener { onDeductionsClick(record) }
        }
    }

    override fun getItemCount(): Int {
        return records.size
    }
}