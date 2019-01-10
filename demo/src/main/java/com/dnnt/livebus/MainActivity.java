package com.dnnt.livebus;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.dnnt.livebus.test.event.TestEvent1;
import com.dnnt.livebus.test.event.TestEvent2;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        test1();
        testPost();
    }

    private void test1(){
        LiveBus.sendEvent(new TestEvent1());
        LiveBus.setAndSendSticky(new TestEvent2());


        LiveBus.get(TestEvent1.class).observe(this, event -> {
            throw new IllegalStateException("Observer should not received event1 once observed");
        });

        LiveBus.get(TestEvent2.class).observe(this,event ->{
            throw new IllegalStateException("Observer should not received event2 once observed or after removed");
        });

        LiveBus.get(TestEvent1.class).observeSticky(this,event -> {
            throw new IllegalStateException("Observer should not received event1 once observed");
        });

        LiveBus.get(TestEvent2.class).observeSticky(this,event -> {
            LiveBus.get(TestEvent1.class).removeObservers(MainActivity.this);
            LiveBus.get(TestEvent2.class).removeObservers(MainActivity.this);
        });

        LiveBus.sendEvent(new TestEvent2());

        // test remove sticky event
        LiveBus.removeStickyEvent(TestEvent2.class);

        LiveBus.get(TestEvent2.class).observeSticky(this,event -> {
            throw new IllegalStateException("Sticky event have been removed, should not receive");
        });

        LiveBus.get(TestEvent2.class).removeObservers(this);
    }

    public void testPost(){
        new Thread(() -> {
            LiveBus.postSetAndSendSticky(new TestEvent2());
        }).start();

        findViewById(R.id.main_root).postDelayed(() -> {
            LiveBus.get(TestEvent2.class).observeSticky(this, event -> {
                TextView tv = findViewById(R.id.main_text);
                tv.setText("Test pass");
                LiveBus.get(TestEvent2.class).removeObservers(this);
            });
        }, 3000);


    }

}
