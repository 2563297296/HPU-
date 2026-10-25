package cn.edu.hpu.powerwidget;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Displays the persistent app log and allows exporting it. */
public final class LogActivity extends Activity {
    private TextView content;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.init(this);
        setContentView(R.layout.activity_log);
        content = findViewById(R.id.log_content);
        findViewById(R.id.log_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        findViewById(R.id.log_export).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { exportLog(); }
        });
        reload();
    }

    @Override protected void onResume() { super.onResume(); reload(); }

    private void reload() {
        File source = AppLogger.logFile(this);
        String text;
        if (source.isFile()) {
            try {
                FileInputStream input = new FileInputStream(source);
                byte[] buffer = new byte[8192];
                StringBuilder sb = new StringBuilder();
                int read;
                while ((read = input.read(buffer)) != -1) sb.append(new String(buffer, 0, read, "UTF-8"));
                input.close();
                text = sb.toString();
            } catch (IOException error) {
                text = "读取日志失败：" + error.getMessage();
            }
        } else {
            text = "暂无日志。";
        }
        content.setText(text);
    }

    private void exportLog() {
        File source = AppLogger.logFile(this);
        if (!source.isFile()) { Toast.makeText(this, "暂无日志", Toast.LENGTH_SHORT).show(); return; }
        try {
            File target = new File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "HPU-power-log.txt");
            target.getParentFile().mkdirs();
            FileInputStream input = new FileInputStream(source);
            FileOutputStream output = new FileOutputStream(target, false);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            input.close(); output.close();
            Toast.makeText(this, "日志已导出: " + target.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException error) {
            Toast.makeText(this, "导出失败: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
