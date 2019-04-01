package oro.watch.java.MonitorDirectoryService.Service.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import oro.watch.java.MonitorDirectoryService.utility.CommonUtils;

@Service
public class DirWatchingService {
	private static final Logger logger = LoggerFactory.getLogger(DirWatchingService.class);
	@Autowired
	private CacheService cacheService;
	@Autowired
	private ParserService parserService;
	private boolean startWatch;
	WatchService watcher;
	private ExecutorService executorService;
	private String watchDir;
	private List<String> fileExtensions;
	/**
	 * maintain a map of watch keys and directories Map<WatchKey, Path> keys to
	 * correctly identify which directory has been modified
	 */
	private Map<WatchKey, Path> keys = new HashMap<WatchKey, Path>();

	public void start(String watchDir, WatchService watcher, ExecutorService executorService,
			List<String> fileExtensions) {
		logger.info("DirWatchingService start() method is called");
		try {
			this.watchDir = watchDir;
			this.watcher = watcher;
			this.executorService = executorService;
			this.fileExtensions = fileExtensions;
			this.walkAndRegisterDirectories(Paths.get(watchDir));
			this.startWatch = true;
			this.startWatching();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Watching the existing and new directory for new file
	 */
	private void startWatching() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (startWatch) {
					WatchKey key;
					try {
						key = watcher.take();
					} catch (InterruptedException x) {
						return;
					}
					Path dir = keys.get(key);
					if (dir == null) {
						System.err.println("WatchKey not recognized!!");
						continue;
					}

					for (WatchEvent<?> event : key.pollEvents()) {
						Path name = ((WatchEvent<Path>) event).context();

						Path child = dir.resolve(name);
						if (Files.isDirectory(child)) {
							try {
								walkAndRegisterDirectories(child);
							} catch (IOException | InterruptedException x) {
								// throw runtime exception
							}
						} else {
							if (CommonUtils.checkFileType(name.toString(), fileExtensions)) {
								createCache(child);
							}
						}
					}

					// reset key and remove from set if directory no longer
					// accessible
					boolean valid = key.reset();
					if (!valid) {
						keys.remove(key);
						// all directories are inaccessible
						if (keys.isEmpty()) {
							break;
						}
					}

				}

			}
		}).start();

	}

	private void registerDirectory(Path dir) throws IOException {
		WatchKey key = dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_MODIFY);
		createCache(dir);
		keys.put(key, dir);
	}

	/**
	 * Register the given directory, and all its sub-directories, with the
	 * WatchService.
	 * 
	 * @throws InterruptedException
	 */
	private void walkAndRegisterDirectories(final Path start) throws IOException, InterruptedException {
		if (start.toFile().isDirectory()) {
			File[] files = start.toFile().listFiles();
			for (int index = 0; index < files.length; index++) {
				Thread.sleep(100);
				File file = files[index];
				if (file.isDirectory()) {
					walkAndRegisterDirectories(Paths.get(file.getAbsolutePath()));
				}
			}
			registerDirectory(start);
		}
	}

	private void createCache(Path path) {
		createCache(path, true);
	}

	private void createCache(Path path, boolean persist) {
		executorService.execute(() -> {
			File fileFolder = path.toFile();
			String relativePath = CommonUtils.relativePath(path.toString(), watchDir);
			if (fileFolder.isFile()) {
				if (CommonUtils.checkFileType(path.getFileName().toString(), fileExtensions)) {
					cacheService.add(relativePath, parserService.parse(path));
					if (persist) {
						cacheService.persist(relativePath);
					}
				}
			} else {
				File[] files = fileFolder.listFiles();
				CountDownLatch latch = new CountDownLatch(files.length);
				for (File file : files) {
					try {
						createCache(Paths.get(file.getAbsolutePath()), false);
					} finally {
						latch.countDown();
					}
				}
				try {
					latch.await();
				} catch (Exception e) {
					//
				}
				if (persist) {
					cacheService.persist(relativePath);
				}
			}
		});

	}

}
