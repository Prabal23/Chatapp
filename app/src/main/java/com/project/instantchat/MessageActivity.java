package com.project.instantchat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.project.instantchat.Adapter.MessageAdapter;
import com.project.instantchat.Fragments.APIService;
import com.project.instantchat.Model.Active;
import com.project.instantchat.Model.Chat;
import com.project.instantchat.Model.User;
import com.project.instantchat.Notifications.Client;
import com.project.instantchat.Notifications.Data;
import com.project.instantchat.Notifications.MyResponse;
import com.project.instantchat.Notifications.Sender;
import com.project.instantchat.Notifications.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MessageActivity extends AppCompatActivity {

    CircleImageView profile_image;
    ImageView back;
    TextView username, allow, decline, sts;
    FirebaseUser fuser;
    DatabaseReference reference, reference1, reference2;
    Button btn_send;
    EditText text_send;
    MessageAdapter messageAdapter;
    List<Chat> mchat;
    RecyclerView recyclerView;
    LinearLayout status;
    LinearLayout bottom;
    Intent intent;
    ValueEventListener seenListener;
    String userid, active_status = "0";
    APIService apiService;
    boolean notify = false;
    int count = 0, del = 0;
    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("");

        back = (ImageView)findViewById(R.id.back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        apiService = Client.getClient("https://fcm.googleapis.com/").create(APIService.class);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(linearLayoutManager);

        profile_image = findViewById(R.id.profile_image);
        username = findViewById(R.id.username);
        btn_send = findViewById(R.id.btn_send);
        text_send = findViewById(R.id.text_send);
        allow = (TextView) findViewById(R.id.allow);
        decline = (TextView) findViewById(R.id.decline);
        sts = (TextView) findViewById(R.id.sts);

        auth = FirebaseAuth.getInstance();

        status = (LinearLayout) findViewById(R.id.status);
        bottom = (LinearLayout) findViewById(R.id.bottom);

        intent = getIntent();
        userid = intent.getStringExtra("userid");
        fuser = FirebaseAuth.getInstance().getCurrentUser();

        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                notify = true;
                String msg = text_send.getText().toString();
                if (!msg.equals("")) {
                    sendMessage(fuser.getUid(), userid, msg);
                    sendStatus();
                } else {
                    Toast.makeText(MessageActivity.this, "You can't send empty message", Toast.LENGTH_SHORT).show();
                }
                text_send.setText("");
            }
        });


        reference = FirebaseDatabase.getInstance().getReference("Users").child(userid);

        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                User user = dataSnapshot.getValue(User.class);
                username.setText(user.getUsername());
                String stss = user.getStatus();
                if (stss.equals("online")) {
                    sts.setBackgroundResource(R.drawable.background_online);
                    sts.setTextColor(Color.WHITE);
                    sts.setText("Online");
                }else{
                    sts.setBackgroundResource(R.drawable.background_offline);
                    sts.setTextColor(Color.GRAY);
                    sts.setText("Offline");
                }
                if (user.getImageURL().equals("default")) {
                    profile_image.setImageResource(R.drawable.user2);
                } else {
                    //and this
                    Glide.with(getApplicationContext()).load(user.getImageURL()).into(profile_image);
                }

                readMesagges(fuser.getUid(), userid, user.getImageURL());
                readStatus(fuser.getUid(), userid);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        seenMessage(userid);

        allow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                messageStatus(userid, fuser.getUid(), "1");
            }
        });

        decline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                messageStatus(userid, fuser.getUid(), "2");
                //chatDelete();
            }
        });
    }

    private void seenMessage(final String userid) {
        reference = FirebaseDatabase.getInstance().getReference("Chats");
        seenListener = reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Chat chat = snapshot.getValue(Chat.class);
                    if (chat.getReceiver().equals(fuser.getUid()) && chat.getSender().equals(userid)) {
                        HashMap<String, Object> hashMap = new HashMap<>();
                        hashMap.put("isseen", true);
                        snapshot.getRef().updateChildren(hashMap);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    //Send Message
    private void sendMessage(String sender, final String receiver, String message) {

        reference = FirebaseDatabase.getInstance().getReference();

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("sender", sender);
        hashMap.put("receiver", receiver);
        hashMap.put("message", message);
        hashMap.put("isseen", false);

        reference.child("Chats").push().setValue(hashMap);


        // add user to chat fragment
        final DatabaseReference chatRef = FirebaseDatabase.getInstance().getReference("Chatlist")
                .child(fuser.getUid())
                .child(userid);

        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    chatRef.child("id").setValue(userid);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        final DatabaseReference chatRefReceiver = FirebaseDatabase.getInstance().getReference("Chatlist")
                .child(userid)
                .child(fuser.getUid());
        chatRefReceiver.child("id").setValue(fuser.getUid());

        final String msg = message;

        reference = FirebaseDatabase.getInstance().getReference("Users").child(fuser.getUid());
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                User user = dataSnapshot.getValue(User.class);
                if (notify) {
                    sendNotifiaction(receiver, user.getUsername(), msg);
                }
                notify = false;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    //Active Status table
    private void messageStatus(String sender, final String receiver, String message) {

        DatabaseReference reference = FirebaseDatabase.getInstance().getReference();

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("sender", sender);
        hashMap.put("receiver", receiver);
        hashMap.put("status", message);

        reference.child("Active").push().setValue(hashMap);
    }

    private void sendNotifiaction(String receiver, final String username, final String message) {
        DatabaseReference tokens = FirebaseDatabase.getInstance().getReference("Tokens");
        Query query = tokens.orderByKey().equalTo(receiver);
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Token token = snapshot.getValue(Token.class);
                    Data data = new Data(fuser.getUid(), R.mipmap.ic_launcher, username + ": " + message, "New Message", userid);

                    Sender sender = new Sender(data, token.getToken());

                    apiService.sendNotification(sender)
                            .enqueue(new Callback<MyResponse>() {
                                @Override
                                public void onResponse(Call<MyResponse> call, Response<MyResponse> response) {
                                    if (response.code() == 200) {
                                        if (response.body().success != 1) {
                                            Toast.makeText(MessageActivity.this, "Failed!", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }

                                @Override
                                public void onFailure(Call<MyResponse> call, Throwable t) {

                                }
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void readMesagges(final String myid, final String userid, final String imageurl) {
        mchat = new ArrayList<>();

        reference = FirebaseDatabase.getInstance().getReference("Chats");
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mchat.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String myParentNode = snapshot.getKey();
                    //Toast.makeText(MessageActivity.this, myParentNode, Toast.LENGTH_SHORT).show();
                    Chat chat = snapshot.getValue(Chat.class);
                    if (chat.getReceiver().equals(myid) && chat.getSender().equals(userid) ||
                            chat.getReceiver().equals(userid) && chat.getSender().equals(myid)) {
                        mchat.add(chat);
                    }

                    messageAdapter = new MessageAdapter(MessageActivity.this, mchat, imageurl);
                    recyclerView.setAdapter(messageAdapter);

                    String rec = chat.getReceiver();
                    String sen = chat.getSender();

                    if (fuser.getUid().equals(rec) && userid.equals(sen)) {
                        del = 1;
                    }
                }
                count = mchat.size();
                //Toast.makeText(MessageActivity.this, "" + count, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void readStatus(final String send, final String receive) {

        reference = FirebaseDatabase.getInstance().getReference("Active");
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Active active = snapshot.getValue(Active.class);
                    active_status = active.getStatus();
                    String rec = active.getReceiver();
                    String sen = active.getSender();
                    /*Toast.makeText(MessageActivity.this, active_status, Toast.LENGTH_SHORT).show();
                    Toast.makeText(MessageActivity.this, "Receiver - " + rec, Toast.LENGTH_SHORT).show();
                    Toast.makeText(MessageActivity.this, "Sender - " + sen, Toast.LENGTH_SHORT).show();*/
                    /*if (rec.equals(send) && sen.equals(receive)) {
                        active_status = active.getStatus();

                        //Toast.makeText(MessageActivity.this, active_status, Toast.LENGTH_SHORT).show();
                    }*/
                    if (active_status.equals("0") && userid.equals(sen)) {
                        //Toast.makeText(MessageActivity.this, "ok", Toast.LENGTH_SHORT).show();
                        status.setVisibility(View.VISIBLE);
                        bottom.setVisibility(View.GONE);
                    } else if (count == 0) {
                        //Toast.makeText(MessageActivity.this, "ok 1", Toast.LENGTH_SHORT).show();
                        status.setVisibility(View.GONE);
                        bottom.setVisibility(View.VISIBLE);
                    } else {
                        //Toast.makeText(MessageActivity.this, fuser.getUid(), Toast.LENGTH_SHORT).show();
                        if (active_status.equals("2") && fuser.getUid().equals(sen)) {
                            //Toast.makeText(MessageActivity.this, "ok 2", Toast.LENGTH_SHORT).show();
                            status.setVisibility(View.GONE);
                            bottom.setVisibility(View.GONE);
                        } else {
                            //Toast.makeText(MessageActivity.this, "ok 4", Toast.LENGTH_SHORT).show();
                            status.setVisibility(View.GONE);
                            bottom.setVisibility(View.VISIBLE);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    public void sendStatus() {
        if (active_status.equals("0")) {
            messageStatus(fuser.getUid(), userid, "0");
        } else if (active_status.equals("2")) {
            messageStatus(userid, fuser.getUid(), "2");
        } else {
            messageStatus(userid, fuser.getUid(), "1");
        }
    }

    public void chatDelete() {
        reference = FirebaseDatabase.getInstance().getReference("Chatlist").child(fuser.getUid()).child(userid);
        reference1 = FirebaseDatabase.getInstance().getReference("Chatlist").child(userid).child(fuser.getUid());
        //reference2=FirebaseDatabase.getInstance().getReference("Chat").child(userid).child(fuser.getUid());
        reference.removeValue();
        reference1.removeValue();
        if (del == 1) {
            delMesagges(fuser.getUid(), userid);
            //delMesagges(fuser.getUid(), userid);
        }
        finish();
    }

    private void delMesagges(final String myid, final String userid) {
        mchat = new ArrayList<>();

        reference = FirebaseDatabase.getInstance().getReference("Chats");
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mchat.clear();
                String myParentNode = "";
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Chat chat = snapshot.getValue(Chat.class);
                    myParentNode = snapshot.getKey();
                    String rec = chat.getReceiver();
                    String sen = chat.getSender();

                    if (fuser.getUid().equals(rec) && userid.equals(sen)) {
                        reference2 = FirebaseDatabase.getInstance().getReference("Chats").child(myParentNode);
                        reference2.removeValue();
                        //active_status = "0";
                        //delStatus();
                    }
                    //Toast.makeText(MessageActivity.this, myParentNode, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void currentUser(String userid) {
        SharedPreferences.Editor editor = getSharedPreferences("PREFS", MODE_PRIVATE).edit();
        editor.putString("currentuser", userid);
        editor.apply();
    }

    private void status(String status) {
        reference = FirebaseDatabase.getInstance().getReference("Users").child(fuser.getUid());

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("status", status);

        reference.updateChildren(hashMap);
    }

    @Override
    protected void onResume() {
        super.onResume();
        status("online");
        currentUser(userid);
    }

    @Override
    protected void onPause() {
        super.onPause();
        reference.removeEventListener(seenListener);
        status("offline");
        currentUser("none");
    }
}
