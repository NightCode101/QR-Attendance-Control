package cics.csup.qrattendancecontrol;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;

public class GraphActivity extends AppCompatActivity {

    private BarChart barChart;
    private Button buttonFirstYear, buttonSecondYear, buttonThirdYear, buttonFourthYear;

    private FirebaseFirestore firestore;
    private int currentYearLevel = 1; // Default to 1st Year

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        // Initialize views
        barChart = findViewById(R.id.barChart);
        buttonFirstYear = findViewById(R.id.buttonFirstYear);
        buttonSecondYear = findViewById(R.id.buttonSecondYear);
        buttonThirdYear = findViewById(R.id.buttonThirdYear);
        buttonFourthYear = findViewById(R.id.buttonFourthYear);

        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance();

        // Set up button listeners
        buttonFirstYear.setOnClickListener(v -> {
            currentYearLevel = 1;
            updateGraphForYear(currentYearLevel);
        });

        buttonSecondYear.setOnClickListener(v -> {
            currentYearLevel = 2;
            updateGraphForYear(currentYearLevel);
        });

        buttonThirdYear.setOnClickListener(v -> {
            currentYearLevel = 3;
            updateGraphForYear(currentYearLevel);
        });

        buttonFourthYear.setOnClickListener(v -> {
            currentYearLevel = 4;
            updateGraphForYear(currentYearLevel);
        });

        // Load the first year data by default
        updateGraphForYear(currentYearLevel);
    }

    private void updateGraphForYear(int year) {
        String sectionRange = getSectionRangeForYear(year); // Get the section range based on the year

        // Firestore query to get the attendance data for all sections in the selected year
        firestore.collection("sections_total").document(sectionRange)
                .collection("attendance")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int totalStudents = 0;
                    int totalPresent = 0;

                    // Loop through each document (each section)
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        totalStudents += document.getLong("total_students").intValue();
                        totalPresent += document.getLong("present_count").intValue();
                    }

                    // Calculate absent students
                    int totalAbsent = totalStudents - totalPresent;

                    // Update the graph with the data
                    updateGraph(totalPresent, totalAbsent, year);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(GraphActivity.this, "Error loading data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateGraph(int totalPresent, int totalAbsent, int year) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, totalPresent));
        entries.add(new BarEntry(1f, totalAbsent));

        BarDataSet dataSet = new BarDataSet(entries, "Year " + year + " Attendance");
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f);

        barChart.setData(barData);

        // X-axis labels
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(Arrays.asList("Present", "Absent")));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);

        // Y-axis settings
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);

        barChart.getAxisRight().setEnabled(false);
        barChart.getDescription().setEnabled(false);
        barChart.invalidate(); // Refresh chart
    }

    private String getSectionRangeForYear(int year) {
        // Return the section range for the selected year
        switch (year) {
            case 1: return "1A-1D";
            case 2: return "2A-2C";
            case 3: return "3A-3C";
            case 4: return "4A-4C";
            default: return "";
        }
    }
}
