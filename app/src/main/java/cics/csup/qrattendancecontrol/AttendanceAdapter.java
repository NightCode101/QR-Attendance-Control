package cics.csup.qrattendancecontrol;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

//
// 1. CHANGED: Now extends ListAdapter for built-in DiffUtil
//
public class AttendanceAdapter extends ListAdapter<AttendanceRecord, AttendanceAdapter.ViewHolder> {

    private OnItemLongClickListener longClickListener;

    //
    // 2. ADDED: The DiffUtil callback to calculate list changes
    //
    private static final DiffUtil.ItemCallback<AttendanceRecord> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AttendanceRecord>() {
                @Override
                public boolean areItemsTheSame(@NonNull AttendanceRecord oldItem, @NonNull AttendanceRecord newItem) {
                    // Use a unique identifier. If 'id' is always 0 from Firestore,
                    // we must use a compound key.
                    if (oldItem.getId() != 0 && newItem.getId() != 0) {
                        return oldItem.getId() == newItem.getId();
                    }
                    // Fallback for items that might not have a local DB ID yet
                    return oldItem.getName().equals(newItem.getName()) &&
                            oldItem.getDate().equals(newItem.getDate()) &&
                            oldItem.getSection().equals(newItem.getSection());
                }

                @Override
                public boolean areContentsTheSame(@NonNull AttendanceRecord oldItem, @NonNull AttendanceRecord newItem) {
                    // Check if all the data visible to the user is the same
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

    //
    // 3. CHANGED: Constructor now calls super(DIFF_CALLBACK)
    //
    public AttendanceAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public AttendanceAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttendanceAdapter.ViewHolder holder, int position) {
        //
        // 4. CHANGED: Get the item using getItem(position)
        //
        AttendanceRecord record = getItem(position);

        holder.nameText.setText(record.getName());
        holder.dateText.setText(record.getDate());
        holder.sectionText.setText(record.getSection());

        // Use the safe getter from your AttendanceRecord model
        holder.timeInAMText.setText("IN AM: " + record.getFieldValue("time_in_am"));
        holder.timeOutAMText.setText("OUT AM: " + record.getFieldValue("time_out_am"));
        holder.timeInPMText.setText("IN PM: " + record.getFieldValue("time_in_pm"));
        holder.timeOutPMText.setText("OUT PM: " + record.getFieldValue("time_out_pm"));

        // Visually distinguish unsynced records
        if (!record.isSynced()) {
            holder.container.setAlpha(0.7f); // Make it slightly transparent
        } else {
            holder.container.setAlpha(1f);
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                // Use holder.getAdapterPosition() for safety
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    longClickListener.onItemLongClick(currentPosition, v);
                }
                return true;
            }
            return false;
        });
    }

    //
    // 5. REMOVED: getItemCount() is now handled by ListAdapter
    //

    //
    // 6. REMOVED: updateList() and getCurrentList()
    //    We will call adapter.getCurrentList() directly from the activity
    //

    //
    // 7. REMOVED: formatTime() helper is no longer needed
    //    (Your AttendanceRecord.getFieldValue() method already handles this)
    //

    // --- ViewHolder (Unchanged) ---
    // --- ViewHolder (Corrected) ---
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText, dateText, sectionText;
        TextView timeInAMText, timeOutAMText, timeInPMText, timeOutPMText;

        // THIS IS THE FIX:
        // Changed from LinearLayout to ConstraintLayout
        androidx.constraintlayout.widget.ConstraintLayout container;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            // This line will now work, because the variable type matches the XML
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

    // --- Long-click listener interface (Unchanged) ---
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(int position, View view);
    }
}