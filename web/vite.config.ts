import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import ui from '@nuxt/ui/vite'

export default defineConfig({
  plugins: [
    vue(),
    ui({
      ui: {
        colors: {
          primary: 'brand',
          neutral: 'stone',
        },
      },
      // Icônes Lucide embarquées au build (pas d'appel à l'API Iconify au runtime).
      icon: { clientBundle: { scan: { globInclude: ['src/**/*.{vue,ts}'] } } },
      // Les déclarations générées (auto-imports.d.ts, components.d.ts) sont
      // commitées : vue-tsc tourne avant vite build en CI.
      autoImport: { dts: 'src/auto-imports.d.ts' },
      components: { dts: 'src/components.d.ts' },
    }),
  ],
  test: {
    environment: 'node',
  },
})
