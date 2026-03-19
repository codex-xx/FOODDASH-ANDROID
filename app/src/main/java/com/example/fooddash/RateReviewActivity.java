package com.example.fooddash;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RateReviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rate_review);

        RatingBar riderRatingBar = findViewById(R.id.riderRatingBar);
        RatingBar restaurantRatingBar = findViewById(R.id.restaurantRatingBar);
        EditText feedbackEditText = findViewById(R.id.feedbackEditText);
        Button submitReviewButton = findViewById(R.id.submitReviewButton);

        submitReviewButton.setOnClickListener(v -> {
            float riderRating = riderRatingBar.getRating();
            float restaurantRating = restaurantRatingBar.getRating();

            if (riderRating <= 0 || restaurantRating <= 0) {
                Toast.makeText(this, "Please rate rider and restaurant.", Toast.LENGTH_SHORT).show();
                return;
            }

            String feedback = feedbackEditText.getText() == null ? "" : feedbackEditText.getText().toString().trim();
            if (feedback.isEmpty()) {
                feedback = "No written feedback";
            }

            Toast.makeText(this, "Thanks for your review!", Toast.LENGTH_LONG).show();

            OrderFlowManager.clearFlow();

            Intent intent = new Intent(this, CustomerDashboard.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
