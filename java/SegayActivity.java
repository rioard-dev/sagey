package id.web.riosages.segay;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import java.lang.Process;
import java.time.Instant;


public class MainActivity extends Activity {

    private WebView myWebView;
    private File currentDirectory;
    private File relativePath;
    private String ubuntuPath;
	private String staticPath;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private File file;
	private String firum;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inisialisasi WebView
        myWebView = new WebView(this);
        setContentView(myWebView);

        currentDirectory = new File(getFilesDir(), ""); // Direkomendasikan buat "home" sendiri
        relativePath = new File("/storage/EE5B-0820/segay/");
        staticPath = getFilesDir().getAbsolutePath();
        ubuntuPath = relativePath + "zenoc";
        if (!currentDirectory.exists()) {
            currentDirectory.mkdirs();
        }

        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
		webSettings.setDomStorageEnabled(true);     // ← Enables localStorage
        webSettings.setDatabaseEnabled(true);

        // Optional: better viewport handling
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        // Untuk keamanan tambahan (disarankan)
        webSettings.setAllowFileAccessFromFileURLs(false);
        webSettings.setAllowUniversalAccessFromFileURLs(false);

        myWebView.setWebViewClient(new WebViewClient());
        // Interface JavaScript
        myWebView.addJavascriptInterface(this, "AndroidInterface");
        myWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                setupSetting();
            }
        });
        
        // Load halaman utama
        myWebView.loadUrl("file:///android_asset/index.html");

        // Status bar hitam + teks putih
        setupStatusBar();

        // Cek permission storage (Android 11+)
    }
	public void setupSetting(){
        addLine("Running Applications...");
		allowStorage();
	}
	private String runUbuntuCommand(String command) {
		String fullCmd = "cd  \"+ staticPath +"\" && " + command;

		return cmdExecute(fullCmd);
	}
	@JavascriptInterface
	public void runCommand(final String cmd) {
		new Thread(new Runnable() {
				@Override
				public void run() {
					String output = runUbuntuCommand(cmd);
					sendToJs("onCommandResult", output);
				}
			}).start();
	}
    private void setupStatusBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			Window window = getWindow();
			// Menambahkan flag untuk menggambar background sistem
			window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

			// Mengatur warna background menjadi hitam
			window.setStatusBarColor(Color.WHITE);

			// Memastikan ikon/teks berwarna putih (Clear Light Status Bar flag)
			/*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				View decor = window.getDecorView();
				// Menghapus flag SYSTEM_UI_FLAG_LIGHT_STATUS_BAR agar teks menjadi putih
				decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
			}*/
		}
    }
    @JavascriptInterface
    public void allowStorage(){
        checkPermissions();
        storagesAdd();
    }
    
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
					
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }
        }
    }

    // ==================== INNER CLASS INTERFACE ====================
    

    @JavascriptInterface
    public String pathDefaultDir() {
        return staticPath;
    }
    
    private void sendToJs(final String function, final String data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String escaped = data.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$").replace("\n", "\\n");
                myWebView.evaluateJavascript(function + "(`" + escaped + "`)", null);
            }
        });
    }
    
    @JavascriptInterface
        public boolean makeDirectory(String dirname) {
            if (dirname == null || dirname.trim().isEmpty()) return false;
            File newDir = new File(currentDirectory, dirname.trim());
            return newDir.mkdirs();
        }

        @JavascriptInterface
        public String listFiles() {
            File[] files = currentDirectory.listFiles();
            if (files == null) return "[]";

            JSONArray array = new JSONArray();
            for (File file : files) {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("name", file.getName());
                    obj.put("isDir", file.isDirectory());
                    obj.put("size", file.length());
                    obj.put("lastModified", file.lastModified());
                } catch (Exception ignored) {}
                array.put(obj);
            }
            return array.toString();
        }

        @JavascriptInterface
        public void moveDirectory(String command) {
            if (!command.startsWith("cd ")) return;

            String target = command.substring(3).trim();

            if (target.equals("..")) {
                File parent = currentDirectory.getParentFile();
                if (parent != null && parent.exists()) {
                    currentDirectory = parent;
                }
            } else {
                File nextDir = target.startsWith("/") ?
                        new File(target) :
                        new File(currentDirectory, target);

                if (nextDir.exists() && nextDir.isDirectory()) {
                    currentDirectory = nextDir;
                } else {
                    addLine("Error: Folder tidak ditemukan: " + target);
                }
            }

            lineHere(currentDirectory.getAbsolutePath());
        }

        @JavascriptInterface
        public String MyRename(String oldPath, String newName) {
            try {
                if (oldPath == null || oldPath.trim().isEmpty() || newName == null || newName.trim().isEmpty()) {
                    addLine("Error: Path atau nama baru tidak boleh kosong");
                    return "INVALID_INPUT";
                }

                File oldFile = new File(currentDirectory, oldPath.trim());
                File newFile = new File(currentDirectory, newName.trim());

                if (newFile.exists()) {
                    addLine("Error: Nama '" + newName + "' sudah digunakan");
                    return "NAME_ALREADY_EXISTS";
                }

                if (oldFile.renameTo(newFile)) {
                    addLine("Successfully renamed: " + oldPath + " → " + newName);
                    return "SUCCESS";
                } else {
                    addLine("Gagal mengubah nama file/folder!");
                    return "RENAME_FAILED";
                }
            } catch (SecurityException e) {
                addLine("Error: Permission ditolak");
                return "PERMISSION_DENIED";
            } catch (Exception e) {
                addLine("Error: " + e.getMessage());
                return "EXCEPTION: " + e.getMessage();
            }
        }

        @JavascriptInterface
        private String cmdExecute(String command) {
            StringBuilder output = new StringBuilder();
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", command});
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line+"\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    output.append("[ERR] " +line+"\n");
                }
                
                process.waitFor();
                
            } catch (Exception e) {
                addLine("Execution Error: " + e.getMessage());
            }
            return output.length() == 0 ? "(No output)" : output.toString();
        }

        @JavascriptInterface
        public void chmodFile(String filePath, boolean makeExecutable) {
            String perm = makeExecutable ? "755" : "644";
            String fullPath = new File(currentDirectory, filePath).getAbsolutePath();
            cmdExecute("chmod " + perm + " \"" + fullPath + "\"");
        }
        
        // Symlink & storages
        @JavascriptInterface
        public void storagesAdd() {
            String target = "/storage/EE5B-0820/segay";
            String targetLocal = "/storage/EE5B-0820/segay/bin";
            
            File sdcardLink = new File(currentDirectory, "segay");
            File binLink = new File(currentDirectory, "bin");
            
            
            if (sdcardLink.exists() && binLink.exists()) {
                
                return;
            }
            makeDirectory("segay");
            String linkPath = sdcardLink.getAbsolutePath();
            
            String linkLocal = binLink.getAbsolutePath();
            if (createSymlink(target, linkPath)){
				
                File zeroFolder = new File(sdcardLink, "segay");
                	
                if (zeroFolder.exists()) {
                    	
                    zeroFolder.renameTo(new File(sdcardLink, "media"));
                }
			
            } else {
                addLine("[X] ERR 127: Failer Symlink");
            }
            
            if(createSymlink(targetLocal,linkLocal)) {
                
                File binFolder = new File(binLink, "bin");
                	
                if (binFolder.exists()) {
                    binFolder.renameTo(new File(binLink, "bin"));
                }
			}else{
				addLine("[X] ERR 127: Failer Symlink");
			}
			makeDirectory("segay/root");
        }

        private boolean createSymlink(String target, String linkPath) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"ln", "-s", target, linkPath});
                int exitCode = process.waitFor();
                return exitCode == 0;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    // ==================== HELPER METHODS ====================

    private void addLine(String text) {
        if (text == null) return;

        final String escaped = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");

        mainHandler.post(new Runnable (){ 
		@Override
		public void run(){
		myWebView.evaluateJavascript("addLine('" + escaped + "')", null);
		}
		});
    }

    private void lineHere(String path) {
        if (path == null) return;
        final String escaped = path.replace("\\", "\\\\").replace("'", "\\'");
		
        mainHandler.post(new Runnable (){ 
			@Override
			public void run(){
				myWebView.evaluateJavascript("LineHere('" + escaped + "')", null);
			}
		});
    }
    public void addLines(final int progress) {
    // Ensure the call happens on the UI Thread
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // This calls the JS function: addLine(progress)
        
                myWebView.evaluateJavascript("showProgress(" + progress + ");", null);
            }
        });
    }
    
    public void prompVContainer(final String truFalse) {
    // Ensure the call happens on the UI Thread
        runOnUiThread(new Runnable() {
        @Override
        public void run() { 
            myWebView.evaluateJavascript("prompVContainer("+truFalse+");", null);
        }
    });
    }
    
    @JavascriptInterface
    public void unzipFile(final String zipFileName, final String zipter) {
        String callbackId = String.valueOf(Instant.now().toEpochMilli());
        prompVContainer("false");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File zipFile = new File(getFilesDir(), zipFileName);
                    if (!zipFile.exists() || !zipFile.isFile()) {
                        throw new IOException("File ZIP tidak ditemukan: " + zipFileName);
                    }
    
                    File destinationFolder = new File(getFilesDir(),zipter);
                    if (!destinationFolder.exists()) {
                        if (!destinationFolder.mkdirs()) {
                            throw new IOException("Gagal membuat folder extracted");
                        }
                    }
    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addLines(0);
                        }
                    });
    
                    unzipWithProgress(zipFile, destinationFolder);
    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addLines(100);
                        }
                    });
    
                } catch (Exception e) {
                    prompVContainer("true");
                } finally{
                    prompVContainer("true");
                }
            }
        }).start();
    }
    private void unzipWithProgress(File zipFile, File destinationFolder) throws IOException {
        byte[] buffer = new byte[8192];
    
        long totalBytes = zipFile.length();
        long bytesRead = 0;
    
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            int entryCount = 0;
            int processedEntries = 0;
    
            // Hitung dulu jumlah entry (opsional, bisa dilewati jika terlalu lambat)
            // atau langsung gunakan perkiraan byte saja
    
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                zis.closeEntry();
            }
    
            // Reset stream (cara sederhana: buka ulang)
            zis.close();
        }
    
        // Buka ulang untuk ekstraksi
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destinationFolder, entry.getName());
    
                // Zip Slip protection
                if (!newFile.getCanonicalPath().startsWith(destinationFolder.getCanonicalPath())) {
                    throw new IOException("Zip Slip detected: " + entry.getName());
                }
    
                if (entry.isDirectory()) {
                    if (!newFile.exists() && !newFile.mkdirs()) {
                        throw new IOException("Gagal membuat direktori: " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Gagal membuat parent dir");
                    }
    
                    long entryBytesRead = 0;
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(newFile))) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            bos.write(buffer, 0, len);
                            entryBytesRead += len;
                            bytesRead += len;
                        }
                    }
    
                    // Update progress berdasarkan bytes (lebih akurat)
                    int progress = (int) ((bytesRead * 100L) / totalBytes);
                    progress = Math.min(100, Math.max(0, progress));
    
                    final int p = progress;
                    runOnUiThread(new Runnable() {
    					@Override
    					public void run() {
    					addLines(p);
    					}
    				});
                }
    
                zis.closeEntry();
            }
        }
    }
    @JavascriptInterface
    public void downloadFile(final String urlString) {
        final String fileName = URLUtil.guessFileName(urlString, null, null);
        
        // UI Update Awal
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                prompVContainer("false");
            }
        });
    
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                InputStream input = null;
                OutputStream output = null;
                
                try {
                    URL url = new URL(urlString);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.connect();
    
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        throw new Exception("HTTP " + connection.getResponseCode());
                    }
    
                    final int fileLength = connection.getContentLength();
                    input = new BufferedInputStream(connection.getInputStream());
                    
                    // Lokasi penyimpanan yang aman di Android modern
                   
                    
                    final File file = new File(getFilesDir(), fileName);
                    output = new FileOutputStream(file);
    
                    byte[] data = new byte[8192];
                    long total = 0;
                    int count;
                    int lastProgress = -1;
    
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        output.write(data, 0, count);
    
                        if (fileLength > 0) {
                            final int progress = (int) ((total * 100) / fileLength);
                            if (progress != lastProgress) {
                                lastProgress = progress;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        addLines(progress);
                                    }
                                });
                            }
                        }
                    }
                    output.flush();
                    
                    // Selesai dengan sukses
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addLines(100);
                            addLine("[OK] Downloaded: " + fileName);
                        }
                    });
    
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addLine("Error: " + e.getMessage());
                        }
                    });
                } finally {
                    // Cleanup resources
                    try {
                        if (output != null) output.close();
                        if (input != null) input.close();
                    } catch (IOException ignored) {}
                    if (connection != null) connection.disconnect();
    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            prompVContainer("true");
                        }
                    });
                } 
            }
        }).start();
    }
	@JavascriptInterface
    public void installations(final String urlString) {
        final String fileName = URLUtil.guessFileName(urlString, null, null);

        // UI Update Awal
        runOnUiThread(new Runnable() {
				@Override
				public void run() {
					prompVContainer("false");
				}
			});

        new Thread(new Runnable() {
				@Override
				public void run() {
					HttpURLConnection connection = null;
					InputStream input = null;
					OutputStream output = null;

					try {
						URL url = new URL(urlString);
						connection = (HttpURLConnection) url.openConnection();
						connection.setConnectTimeout(15000);
						connection.setReadTimeout(15000);
						connection.connect();

						if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
							throw new Exception("HTTP " + connection.getResponseCode());
						}

						final int fileLength = connection.getContentLength();
						input = new BufferedInputStream(connection.getInputStream());

						// Lokasi penyimpanan yang aman di Android modern


						final File file = new File(getFilesDir(), fileName);
						output = new FileOutputStream(file);

						byte[] data = new byte[8192];
						long total = 0;
						int count;
						int lastProgress = -1;

						while ((count = input.read(data)) != -1) {
							total += count;
							output.write(data, 0, count);

							if (fileLength > 0) {
								final int progress = (int) ((total * 100) / fileLength);
								if (progress != lastProgress) {
									lastProgress = progress;
									runOnUiThread(new Runnable() {
											@Override
											public void run() {
												addLines(progress);
											}
										});
								}
							}
						}
						output.flush();

						// Selesai dengan sukses
						runOnUiThread(new Runnable() {
								@Override
								public void run() {
									addLines(100);
								
								}
							});

					} catch (final Exception e) {
						runOnUiThread(new Runnable() {
								@Override
								public void run() {
									addLine("Error: " + e.getMessage());
								}
							});
					} finally {
						// Cleanup resources
						try {
							if (output != null) output.close();
							if (input != null) input.close();
						} catch (IOException ignored) {}
						if (connection != null) connection.disconnect();

						runOnUiThread(new Runnable() {
								@Override
								public void run() {
									prompVContainer("false");
									unzipFile("packages.zip","/segay");
								}
							});
					} 
				}
			}).start();
    }
    @Override
    public void onBackPressed() {
        if (myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
