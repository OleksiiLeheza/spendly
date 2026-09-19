// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');
const boundaries = require('eslint-plugin-boundaries');
const prettier = require('eslint-config-prettier');

module.exports = defineConfig([
  {
    // Generated from the gateway's OpenAPI document; regenerate rather than edit.
    ignores: ['src/app/core/api-client/**', 'dist/**', '.angular/**', 'coverage/**'],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
      prettier,
    ],
    processor: angular.processInlineTemplates,
    plugins: { boundaries },
    settings: {
      // Without a TypeScript resolver the plugin cannot follow extensionless imports, so
      // every dependency resolves to "unknown" and the policies silently never fire.
      'import/resolver': {
        typescript: { alwaysTryTypes: true, project: 'tsconfig.json' },
      },
      'boundaries/elements': [
        { type: 'core', pattern: 'src/app/core' },
        { type: 'layout', pattern: 'src/app/layout' },
        { type: 'feature', pattern: 'src/app/features/*/feature', capture: ['featureName'] },
        { type: 'data-access', pattern: 'src/app/features/*/data-access', capture: ['featureName'] },
        { type: 'ui', pattern: 'src/app/features/*/ui', capture: ['featureName'] },
        { type: 'shared-ui', pattern: 'src/app/shared/ui' },
        { type: 'util', pattern: 'src/app/shared/util' },
        { type: 'app', pattern: 'src/app' },
      ],
    },
    rules: {
      '@angular-eslint/directive-selector': ['error', { type: 'attribute', prefix: 'app', style: 'camelCase' }],
      '@angular-eslint/component-selector': ['error', { type: 'element', prefix: 'app', style: 'kebab-case' }],
      '@angular-eslint/prefer-on-push-component-change-detection': 'error',
      '@angular-eslint/prefer-standalone': 'error',
      '@angular-eslint/use-lifecycle-interface': 'error',

      // CLAUDE.md 9.2. A rule a linter checks is a rule; a rule in a document is a wish.
      'boundaries/dependencies': [
        'error',
        {
          default: 'disallow',
          policies: [
            {
              from: { element: { type: 'app' } },
              allow: {
                to: { element: { types: { anyOf: ['core', 'layout', 'feature', 'shared-ui', 'util'] } } },
              },
            },
            {
              from: { element: { type: 'core' } },
              allow: { to: { element: { types: { anyOf: ['core', 'util'] } } } },
            },
            {
              from: { element: { type: 'layout' } },
              allow: { to: { element: { types: { anyOf: ['core', 'shared-ui', 'util'] } } } },
            },
            {
              from: { element: { type: 'feature' } },
              allow: { to: { element: { types: { anyOf: ['core', 'shared-ui', 'util'] } } } },
            },
            {
              // Only this feature's own data-access and ui: feature -X another feature.
              from: { element: { type: 'feature' } },
              allow: {
                to: {
                  element: {
                    types: { anyOf: ['data-access', 'ui'] },
                    captured: { featureName: '{{from.element.captured.featureName}}' },
                  },
                },
              },
            },
            {
              from: { element: { type: 'data-access' } },
              allow: { to: { element: { types: { anyOf: ['core', 'util'] } } } },
            },
            {
              // A dumb component never fetches: no data-access, no core services.
              from: { element: { type: 'ui' } },
              allow: { to: { element: { types: { anyOf: ['shared-ui', 'util'] } } } },
            },
            {
              from: { element: { type: 'shared-ui' } },
              allow: { to: { element: { type: 'util' } } },
            },
            {
              from: { element: { type: 'util' } },
              allow: { to: { element: { type: 'util' } } },
            },
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
