package net.micode.notes.ui;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.jetbrains.annotations.Nullable;

import androidx.appcompat.app.AppCompatActivity;

import net.micode.notes.MyDatabaseOpenHelper;
import net.micode.notes.R;

public class PasswordActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "PasswordActivity";


    MyDatabaseOpenHelper dpHelper = new MyDatabaseOpenHelper(this, "password.db", null, 2);


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: in PasswordActivity");
        setContentView(R.layout.activity_password);

        dpHelper.getWritableDatabase();

        SharedPreferences sp = getSharedPreferences("config", Context.MODE_PRIVATE);
        boolean isFirst = sp.getBoolean("isFirst", true);
        if (isFirst) {
            // 第一次执行该方法时需要执行的操作
            SQLiteDatabase db = dpHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("id",1);
            values.put("password","123456");
            db.insert("password",null,values);

            // 将isFirst标记为false
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean("isFirst", false);
            editor.apply();
        }


            Button ok = findViewById(R.id.ok);
            Button changePassword = findViewById(R.id.change);

            ok.setOnClickListener(this);
            changePassword.setOnClickListener(this);
        }

        @Override
        public void onClick (View view){
            switch (view.getId()) {
                case R.id.ok:
                    boolean success = false;
                    EditText input = findViewById(R.id.password_input);
                    String inputPassword = String.valueOf(input.getText());
                    Log.d(TAG, "onClick: inputPassword=" + inputPassword);

                    Log.d(TAG, "onClick: clicked ok");

                    SQLiteDatabase db = dpHelper.getWritableDatabase();
                    Cursor cursor = db.query("password", null, null, null,
                            null, null, null);
                    Log.d(TAG, "onClick: query success");
                    if (cursor.moveToFirst()) {
                        do {
                            //遍历Cursor对象，取出数据并打印
                            // 忽略警告
                            @SuppressLint("Range")
                            String password = cursor.getString(cursor.getColumnIndex("password"));
                            if (password.equals(inputPassword)) {
                                success = true;
                                break;
                            }
                            Log.d(TAG, "password is " + password);
                        } while (cursor.moveToNext());
                        if (!success) {
                            Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                    cursor.close();

                    if (success) {
                        Intent intent = new Intent(PasswordActivity.this, NotesListActivity.class);
                        startActivity(intent);

                        Toast.makeText(this, "密码正确", Toast.LENGTH_SHORT).show();

                        finish();
                    }
                    break;
                case R.id.change:
                    Intent intent = new Intent(PasswordActivity.this, ChangePasswordActivity.class);
                    startActivity(intent);
                    break;
                default:
                    break;
            }
        }
    }
