package com.ylx.myocrdemo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class ResponseResultActivity extends AppCompatActivity {

    private TextView mContentTxt;

    private static final String RESULT_CONTENT = "result_content";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_response_result);
        mContentTxt = (TextView) findViewById(R.id.content_txt);
        mContentTxt.setText("扫描结果：" + getIntent().getStringExtra(RESULT_CONTENT));
    }

    public static void jumpResponseResultActivity(Context mActivity, String content){
        Intent intent = new Intent(mActivity, ResponseResultActivity.class);
        intent.putExtra(RESULT_CONTENT, content);
        mActivity.startActivity(intent);
    }

}
