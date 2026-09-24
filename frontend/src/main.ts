import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';
import { RUNTIME_CONFIG, loadRuntimeConfig } from './app/core/runtime-config';

loadRuntimeConfig()
  .then((config) =>
    bootstrapApplication(App, {
      ...appConfig,
      providers: [...appConfig.providers, { provide: RUNTIME_CONFIG, useValue: config }],
    }),
  )
  .catch((error) => console.error(error));
