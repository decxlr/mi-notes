package net.micode.notes.ui;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import net.micode.notes.MyDatabaseOpenHelper;
import net.micode.notes.R;

public class ChangePasswordActivity extends AppCompatActivity {

    MyDatabaseOpenHelper dpHelper = new MyDatabaseOpenHelper(this, "password.db", null, 2);

    private static final String TAG = "ChangePasswordActivity";
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_change_password);

        Button back = findViewById(R.id.back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        EditText input = findViewById(R.id.password_input);
        EditText inputNew = findViewById(R.id.password_input_new);

        Button ok = findViewById(R.id.ok);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean success = false;

                String inputPassword = String.valueOf(input.getText());
                String inputNewPassword = String.valueOf(inputNew.getText());

                Log.d(TAG, "onClick: inputPassword=" + inputPassword);
                Log.d(TAG, "onClick: inputNewPassword=" + inputNewPassword);

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
                        Toast.makeText(ChangePasswordActivity.this, "原密码错误", Toast.LENGTH_SHORT).show();
                    }
                }
                cursor.close();

                if (success) {
                    // 修改密码
//            SQLiteDatabase db2 = dpHelper.getWritableDatabase();
                    ContentValues contentValues = new ContentValues();
                    contentValues.put("password",inputNewPassword);
                    db.update("password",contentValues,"password=?",new String[]{inputPassword});

                    Toast.makeText(ChangePasswordActivity.this, "密码修改成功", Toast.LENGTH_SHORT).show();
                }
            }
        });


    }
}
