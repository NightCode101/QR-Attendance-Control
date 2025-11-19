package cics.csup.qrattendancecontrol;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

public class AttendanceAdapter extends ListAdapter<AttendanceRecord, AttendanceAdapter.ViewHolder> {

    private OnItemLongClickListener longClickListener;

    private static final DiffUtil.ItemCallback<AttendanceRecord> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AttendanceRecord>() {
                @Override
                public boolean areItemsTheSame(@NonNull AttendanceRecord oldItem, @NonNull AttendanceRecord newItem) {
                    if (oldItem.getId() != 0 && newItem.getId() != 0) {
                        return oldItem.getId() == newItem.getId();
                    }
                    return oldItem.getName().equals(newItem.getName()) &&
                            oldItem.getDate().equals(newItem.getDate()) &&
                            oldItem.getSection().equals(newItem.getSection());
                }

                @Override
                public boolean areContentsTheSame(@NonNull AttendanceRecord oldItem, @NonNull AttendanceRecord newItem) {
                    return oldItem.getName().equals(newItem.getName()) &&
                            oldItem.getDate().equals(newItem.getDate()) &&
                            oldItem.getSection().equals(newItem.getSection()) &&
                            Objects.equals(oldItem.getTimeInAM(), newItem.getTimeInAM()) &&
                            Objects.equals(oldItem.getTimeOutAM(), newItem.getTimeOutAM()) &&
                            Objects.equals(oldItem.getTimeInPM(), newItem.getTimeInPM()) &&
                            Objects.equals(oldItem.getTimeOutPM(), newItem.getTimeOutPM()) &&
                            oldItem.isSynced() == newItem.isSynced();
                }
            };

    public AttendanceAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceRecord record = getItem(position);

        holder.nameText.setText(record.getName());
        holder.dateText.setText(record.getDate());
        holder.sectionText.setText(record.getSection());

        holder.timeInAMText.setText("IN AM: " + record.getFieldValue("time_in_am"));
        holder.timeOutAMText.setText("OUT AM: " + record.getFieldValue("time_out_am"));
        holder.timeInPMText.setText("IN PM: " + record.getFieldValue("time_in_pm"));
        holder.timeOutPMText.setText("OUT PM: " + record.getFieldValue("time_out_pm"));

        if (!record.isSynced()) {
            holder.container.setAlpha(0.7f);
        } else {
            holder.container.setAlpha(1f);
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    longClickListener.onItemLongClick(currentPosition, v);
                }
                return true;
            }
            return false;
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText, dateText, sectionText;
        TextView timeInAMText, timeOutAMText, timeInPMText, timeOutPMText;
        androidx.constraintlayout.widget.ConstraintLayout container;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.container);
            nameText = itemView.findViewById(R.id.textName);
            dateText = itemView.findViewById(R.id.textDate);
            sectionText = itemView.findViewById(R.id.textSection);
            timeInAMText = itemView.findViewById(R.id.textTimeInAM);
            timeOutAMText = itemView.findViewById(R.id.textTimeOutAM);
            timeInPMText = itemView.findViewById(R.id.textTimeInPM);
            timeOutPMText = itemView.findViewById(R.id.textTimeOutPM);
        }
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(int position, View view);
    }
}