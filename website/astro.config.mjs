// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import mermaid from 'astro-mermaid';
import starlightLinksValidator from 'starlight-links-validator';

// https://astro.build/config
export default defineConfig({
	site: 'https://evsinev.github.io',
	base: '/deploy',
	integrations: [
		// Must run before Starlight so ```mermaid fences render as diagrams (not code blocks).
		mermaid({ theme: 'default', autoTheme: true }),
		starlight({
			title: 'deploy',
			description:
				'Deployment-orchestration server: WebSocket agents, Redmine-driven deploys, aliases, durable queues, live dashboard',
			customCss: ['./src/styles/mermaid.css'],
			plugins: [starlightLinksValidator()],
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/evsinev/deploy' },
			],
			sidebar: [
				{ label: 'Start here', items: ['index', 'installation', 'configuration'] },
				{ label: 'Guides', items: [{ autogenerate: { directory: 'guides' } }] },
				{ label: 'Reference', items: [{ autogenerate: { directory: 'reference' } }] },
				{ label: 'Changelog', slug: 'changelog' },
			],
		}),
	],
});
