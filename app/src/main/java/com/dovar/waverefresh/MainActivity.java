package com.dovar.waverefresh;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView lsv= (ListView) findViewById(R.id.pullable);

        ArrayList<String> strs=new ArrayList<>();
        for(int i=0;i<50;i++){
            strs.add("1");
        }
        lsv.setAdapter(new ArrayAdapter<>(this,R.layout.item,R.id.name,strs));

    }
}
