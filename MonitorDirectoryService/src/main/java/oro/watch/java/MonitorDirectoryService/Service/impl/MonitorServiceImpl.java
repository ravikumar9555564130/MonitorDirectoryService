package oro.watch.java.MonitorDirectoryService.Service.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.naming.ConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import oro.watch.java.MonitorDirectoryService.Service.MonitorService;
import oro.watch.java.MonitorDirectoryService.constant.AppConstants;

@Service
public class MonitorServiceImpl implements MonitorService {

	private static final Logger logger = LoggerFactory.getLogger(MonitorServiceImpl.class);

	@Autowired
	private Environment env;
	@Autowired
	private CacheService cacheService;

	@Autowired
	private DirWatchingService dirwatchingservice;
	private List<String> fileExtensions;
	private ExecutorService executorService;
	private WatchService watcher;
	private String watchDir;

	@Override
	public void init() throws ConfigurationException {
		logger.info("MonitorServiceImpl init() method is called");
		if (!env.containsProperty(AppConstants.WATCH_DIR_PATH_PROPERTY)) {
			throw new ConfigurationException("Watch directory not defined.");
		}

		watchDir = env.getProperty(AppConstants.WATCH_DIR_PATH_PROPERTY);

		File cacheFolder = new File(watchDir);
		if (!cacheFolder.exists()) {
			throw new ConfigurationException("Cache directory does not exist.");
		}

		if (!cacheFolder.isDirectory()) {
			throw new ConfigurationException("Cache directory property is not a directory.");
		}

		watchDir = cacheFolder.getAbsolutePath();

		if (env.containsProperty(AppConstants.FILE_EXTENSIONS_PROPERTY)) {
			String fileExtensionSeparator = env.containsProperty(AppConstants.FILE_EXTENSIONS_SEPARATOR_PROPERTY)
					? env.getProperty(AppConstants.FILE_EXTENSIONS_SEPARATOR_PROPERTY)
					: AppConstants.FILE_EXTENSIONS_SEPARATOR_DEFAULT;
			fileExtensions = Arrays
					.asList(env.getProperty(AppConstants.FILE_EXTENSIONS_PROPERTY).split(fileExtensionSeparator));
		} else {
			fileExtensions = Arrays.asList(AppConstants.FILE_EXTENSIONS_DEFAULT);

		}

		/**
		 * Creates a WatchService and registers the given directory
		 */
		try {
			this.watcher = FileSystems.getDefault().newWatchService();
			executorService = Executors.newFixedThreadPool(5);
		} catch (IOException e) {
			throw new ConfigurationException(e.getMessage());
		}
		cacheService.init(env);

		dirwatchingservice.start(watchDir, watcher, executorService, fileExtensions);

	}

}
