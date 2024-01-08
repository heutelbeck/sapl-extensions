/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * NOTICE: this is an auto-generated file
 *
 * This file has been generated by the `flow:prepare-frontend` maven goal.
 * This file will be overwritten on every run. Any custom changes should be made to webpack.config.js
 */
const fs = require('fs');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CompressionPlugin = require('compression-webpack-plugin');
const { InjectManifest } = require('workbox-webpack-plugin');
const { DefinePlugin } = require('webpack');
const ExtraWatchWebpackPlugin = require('extra-watch-webpack-plugin');
const ForkTsCheckerWebpackPlugin = require('fork-ts-checker-webpack-plugin');

// Flow plugins
const BuildStatusPlugin = require('@vaadin/build-status-plugin');
const ThemeLiveReloadPlugin = require('@vaadin/theme-live-reload-plugin');
const {
  ApplicationThemePlugin,
  processThemeResources,
  extractThemeName,
  findParentThemes
} = require('@vaadin/application-theme-plugin');

const path = require('path');

// this matches /themes/my-theme/ and is used to check css url handling and file path build.
const themePartRegex = /(\\|\/)themes\1[\s\S]*?\1/;

// the folder of app resources:
//  - flow templates for classic Flow
//  - client code with index.html and index.[ts/js] for CCDM
const frontendFolder = path.resolve(__dirname, 'frontend');
const frontendGeneratedFolder = path.resolve(__dirname, 'frontend/generated');
const fileNameOfTheFlowGeneratedMainEntryPoint = path.resolve(__dirname, 'target/frontend/generated-flow-imports.js');
const mavenOutputFolderForFlowBundledFiles = path.resolve(__dirname, 'target/classes/META-INF/VAADIN/webapp');
const mavenOutputFolderForResourceFiles = path.resolve(__dirname, 'target/classes/META-INF/VAADIN');
const useClientSideIndexFileForBootstrapping = true;
const clientSideIndexHTML = './index.html';
const clientSideIndexEntryPoint = path.resolve(__dirname, 'frontend', 'generated/', 'vaadin.ts');;
const pwaEnabled = false;
const offlinePath = 'offline.html';
const clientServiceWorkerEntryPoint = path.resolve(__dirname, 'target/sw');
// public path for resources, must match Flow VAADIN_BUILD
const VAADIN = 'VAADIN';
const build = 'build';
// public path for resources, must match the request used in flow to get the /build/stats.json file
const config = 'config';
const outputFolder = mavenOutputFolderForFlowBundledFiles;
const indexHtmlPath = 'index.html';
// folder for outputting vaadin-bundle and other fragments
const buildFolder = path.resolve(outputFolder, VAADIN, build);
// folder for outputting stats.json
const confFolder = path.resolve(mavenOutputFolderForResourceFiles, config);
const serviceWorkerPath = 'sw.js';
// file which is used by flow to read templates for server `@Id` binding
const statsFile = `${confFolder}/stats.json`;

// Folders in the project which can contain static assets.
const projectStaticAssetsFolders = [
  path.resolve(__dirname, 'src', 'main', 'resources', 'META-INF', 'resources'),
  path.resolve(__dirname, 'src', 'main', 'resources', 'static'),
  frontendFolder
];

const projectStaticAssetsOutputFolder = path.resolve(__dirname, 'target/classes/META-INF/VAADIN/webapp/VAADIN/static');

// Folders in the project which can contain application themes
const themeProjectFolders = projectStaticAssetsFolders.map((folder) => path.resolve(folder, 'themes'));

const tsconfigJsonFile = path.resolve(__dirname, 'tsconfig.json');
const enableTypeScript = fs.existsSync(tsconfigJsonFile);

// Target flow-fronted auto generated to be the actual target folder
const flowFrontendFolder = path.resolve(__dirname, 'target/flow-frontend');

const statsSetViaCLI = process.argv.find((v) => v.indexOf('--stats') >= 0);
const devMode = process.argv.find((v) => v.indexOf('webpack-dev-server') >= 0);
if (!devMode) {
  // make sure that build folder exists before outputting anything
  const mkdirp = require('mkdirp');
  mkdirp(buildFolder);
  mkdirp(confFolder);
}

let stats;

// Open a connection with the Java dev-mode handler in order to finish
// webpack-dev-mode when it exits or crashes.
const watchDogPort = devMode && process.env.watchDogPort;
if (watchDogPort) {
  const runWatchDog = () => {
    const client = new require('net').Socket();
    client.setEncoding('utf8');
    client.on('error', function () {
      console.log('Watchdog connection error. Terminating webpack process...');
      client.destroy();
      process.exit(0);
    });
    client.on('close', function () {
      client.destroy();
      runWatchDog();
    });

    client.connect(watchDogPort, 'localhost');
  };
  runWatchDog();
}

// Compute the entries that webpack have to visit
const webPackEntries = {};
if (useClientSideIndexFileForBootstrapping) {
  webPackEntries.bundle = clientSideIndexEntryPoint;
  const dirName = path.dirname(fileNameOfTheFlowGeneratedMainEntryPoint);
  const baseName = path.basename(fileNameOfTheFlowGeneratedMainEntryPoint, '.js');
  if (
    fs
      .readdirSync(dirName)
      .filter((fileName) => !fileName.startsWith(baseName) && fileName.endsWith('.js') && fileName.includes('-')).length
  ) {
    // if there are vaadin exported views, add a second entry
    webPackEntries.export = fileNameOfTheFlowGeneratedMainEntryPoint;
  }
} else {
  webPackEntries.bundle = fileNameOfTheFlowGeneratedMainEntryPoint;
}

const appShellUrl = '.';
let appShellManifestEntry = undefined;

const swManifestTransform = (manifestEntries) => {
  const warnings = [];
  const manifest = manifestEntries;
  if (useClientSideIndexFileForBootstrapping) {
    // `index.html` is a special case: in contrast with the JS bundles produced by webpack
    // it's not served as-is directly from the webpack output at `/index.html`.
    // It goes through IndexHtmlRequestHandler and is served at `/`.
    //
    // TODO: calculate the revision based on the IndexHtmlRequestHandler-processed content
    // of the index.html file
    const indexEntryIdx = manifest.findIndex((entry) => entry.url === 'index.html');
    if (indexEntryIdx !== -1) {
      manifest[indexEntryIdx].url = appShellUrl;
      appShellManifestEntry = manifest[indexEntryIdx];
    } else {
      // Index entry is only emitted on first compilation. Make sure it is cached also for incremental builds
      manifest.push(appShellManifestEntry);
    }
  }
  return { manifest, warnings };
};

const createServiceWorkerPlugin = function () {
  return new InjectManifest({
    swSrc: clientServiceWorkerEntryPoint,
    swDest: serviceWorkerPath,
    manifestTransforms: [swManifestTransform],
    maximumFileSizeToCacheInBytes: 100 * 1024 * 1024,
    dontCacheBustURLsMatching: /.*-[a-z0-9]{20}\.cache\.js/,
    include: [
      (chunk) => {
        return true;
      }
    ],
    webpackCompilationPlugins: [
      new DefinePlugin({
        OFFLINE_PATH: JSON.stringify(offlinePath)
      })
    ]
  });
};

const flowFrontendThemesFolder = path.resolve(flowFrontendFolder, 'themes');
const themeOptions = {
  devMode: devMode,
  // The following matches folder 'target/flow-frontend/themes/'
  // (not 'frontend/themes') for theme in JAR that is copied there
  themeResourceFolder: flowFrontendThemesFolder,
  themeProjectFolders: themeProjectFolders,
  projectStaticAssetsOutputFolder: projectStaticAssetsOutputFolder,
  frontendGeneratedFolder: frontendGeneratedFolder
};
let themeName = undefined;
let themeWatchFolders = undefined;
if (devMode) {
  // Current theme name is being extracted from theme.js located in frontend
  // generated folder
  themeName = extractThemeName(frontendGeneratedFolder);
  const parentThemePaths = findParentThemes(themeName, themeOptions);
  const currentThemeFolders = [
    ...projectStaticAssetsFolders.map((folder) => path.resolve(folder, 'themes', themeName)),
    path.resolve(flowFrontendThemesFolder, themeName)
  ];
  // Watch the components folders for component styles update in both
  // current theme and parent themes. Other folders or CSS files except
  // 'styles.css' should be referenced from `styles.css` anyway, so no need
  // to watch them.
  themeWatchFolders = [...currentThemeFolders, ...parentThemePaths].map((themeFolder) =>
    path.resolve(themeFolder, 'components')
  );
}

const processThemeResourcesCallback = (logger) => processThemeResources(themeOptions, logger);

exports = {
  frontendFolder: `${frontendFolder}`,
  buildFolder: `${buildFolder}`,
  confFolder: `${confFolder}`
};

module.exports = {
  mode: 'production',
  context: frontendFolder,
  entry: webPackEntries,

  output: {
    filename: `${VAADIN}/${build}/vaadin-[name]-[contenthash].cache.js`,
    path: outputFolder
  },

  resolve: {
    // Search for import 'x/y' inside these folders, used at least for importing an application theme
    modules: ['node_modules', flowFrontendFolder, ...projectStaticAssetsFolders],
    extensions: [enableTypeScript && '.ts', '.js'].filter(Boolean),
    alias: {
      Frontend: frontendFolder
    }
  },

  stats: devMode && !statsSetViaCLI ? 'errors-warnings' : 'normal', // Unclutter output in dev mode

  devServer: {
    hot: false, // disable HMR
    client: false, // disable wds client as we handle reloads and errors better
    // webpack-dev-server serves ./, webpack-generated, and java webapp
    static: [outputFolder, path.resolve(__dirname, 'src', 'main', 'webapp')],
    onAfterSetupMiddleware: function (devServer) {
      devServer.app.get(`/assetsByChunkName`, function (req, res) {
        res.json(stats.assetsByChunkName);
      });
      devServer.app.get(`/stop`, function (req, res) {
        // eslint-disable-next-line no-console
        console.log("Stopped 'webpack-dev-server'");
        process.exit(0);
      });
    }
  },

  module: {
    rules: [
      enableTypeScript && {
        test: /\.ts$/,
        loader: 'esbuild-loader',
        options: {
          loader: 'ts',
          target: 'es2019'
        }
      },
      {
        test: /\.css$/i,
        use: [
          {
            loader: 'lit-css-loader',
            options: {
              import: 'lit'
            }
          },
          {
            loader: 'extract-loader'
          },
          {
            loader: 'css-loader',
            options: {
              url: (url, resourcePath) => {
                // Only translate files from node_modules
                const resolve = resourcePath.match(/(\\|\/)node_modules\1/);
                const themeResource = resourcePath.match(themePartRegex) && url.match(/^themes\/[\s\S]*?\//);
                return resolve || themeResource;
              },
              // use theme-loader to also handle any imports in css files
              importLoaders: 1
            }
          },
          {
            // theme-loader will change any url starting with './' to start with 'VAADIN/static' instead
            // NOTE! this loader should be here so it's run before css-loader as loaders are applied Right-To-Left
            loader: '@vaadin/theme-loader',
            options: {
              devMode: devMode
            }
          }
        ]
      },
      {
        // File-loader only copies files used as imports in .js files or handled by css-loader
        test: /\.(png|gif|jpg|jpeg|svg|eot|woff|woff2|otf|ttf)$/,
        use: [
          {
            loader: 'file-loader',
            options: {
              outputPath: 'VAADIN/static/',
              name(resourcePath, resourceQuery) {
                if (resourcePath.match(/(\\|\/)node_modules\1/)) {
                  return /(\\|\/)node_modules\1(?!.*node_modules)([\S]+)/.exec(resourcePath)[2].replace(/\\/g, '/');
                }
                if (resourcePath.match(/(\\|\/)flow-frontend\1/)) {
                  return /(\\|\/)flow-frontend\1(?!.*flow-frontend)([\S]+)/.exec(resourcePath)[2].replace(/\\/g, '/');
                }
                return '[path][name].[ext]';
              }
            }
          }
        ]
      }
    ].filter(Boolean)
  },
  performance: {
    maxEntrypointSize: 2097152, // 2MB
    maxAssetSize: 2097152 // 2MB
  },
  plugins: [
    new ApplicationThemePlugin(themeOptions),

    ...(devMode && themeName
      ? [
          new ExtraWatchWebpackPlugin({
            files: [],
            dirs: themeWatchFolders
          }),
          new ThemeLiveReloadPlugin(processThemeResourcesCallback)
        ]
      : []),

    function (compiler) {
      // V14 bootstrapping needs the bundle names
      compiler.hooks.afterEmit.tapAsync("FlowStatsHelper", (compilation, done) => {
        let miniStats = {
          assetsByChunkName: compilation.getStats().toJson().assetsByChunkName
        };
        if (!devMode) {
          fs.writeFile(statsFile, JSON.stringify(miniStats, null, 1),
            () => done());
        } else {
          stats = miniStats;
          done();
        }
      });
    },

    // Includes JS output bundles into "index.html"
    useClientSideIndexFileForBootstrapping &&
      new HtmlWebpackPlugin({
        template: clientSideIndexHTML,
        filename: indexHtmlPath,
        inject: 'head',
        scriptLoading: 'defer',
        chunks: ['bundle']
      }),

    // Service worker for offline
    pwaEnabled && createServiceWorkerPlugin(),

    // Generate compressed bundles when not devMode
    !devMode && new CompressionPlugin(),

    enableTypeScript &&
      new ForkTsCheckerWebpackPlugin({
        typescript: {
          configFile: tsconfigJsonFile
        }
      }),

    new BuildStatusPlugin()
  ].filter(Boolean)
};
