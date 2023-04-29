package net.micode.notes;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class MyDatabaseOpenHelper extends SQLiteOpenHelper {

    private static final String TAG = "MyDatabaseOpenHelper";

    public static final String CREATE_TABLE = "create table password("
            + "id integer primary key autoincrement,"
            + "password text)";

    private Context mContext;

    public MyDatabaseOpenHelper(@Nullable Context context, @Nullable String name,
                                @Nullable SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

        sqLiteDatabase.execSQL(CREATE_TABLE);
        Log.d(TAG, "onCreate: sqLiteDatabase.execSQL(CREATE_TABLE);");
        Toast.makeText(mContext, "Created Table", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }
}
