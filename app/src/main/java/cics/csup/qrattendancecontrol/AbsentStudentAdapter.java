package cics.csup.qrattendancecontrol;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

public class AbsentStudentAdapter extends ListAdapter<AdminCacheDBHelper.StudentML, AbsentStudentAdapter.ViewHolder> {

    private OnItemLongClickListener longClickListener;

    private static final DiffUtil.ItemCallback<AdminCacheDBHelper.StudentML> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AdminCacheDBHelper.StudentML>() {
                @Override
                public boolean areItemsTheSame(@NonNull AdminCacheDBHelper.StudentML oldItem, @NonNull AdminCacheDBHelper.StudentML newItem) {
                    return oldItem.studentID.equals(newItem.studentID);
                }

                @Override
                public boolean areContentsTheSame(@NonNull AdminCacheDBHelper.StudentML oldItem, @NonNull AdminCacheDBHelper.StudentML newItem) {
                    return oldItem.name.equals(newItem.name) && oldItem.section.equals(newItem.section);
                }
            };

    public AbsentStudentAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_absent_student, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdminCacheDBHelper.StudentML student = getItem(position);

        holder.nameText.setText(student.name);
        holder.idText.setText(student.studentID);
        holder.sectionText.setText(student.section);

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(student, holder.getAdapterPosition());
                return true;
            }
            return false;
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText, idText, sectionText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.absentNameText);
            idText = itemView.findViewById(R.id.absentIdText);
            sectionText = itemView.findViewById(R.id.absentSectionText);
        }
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(AdminCacheDBHelper.StudentML student, int position);
    }
}
