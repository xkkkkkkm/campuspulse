import fs from 'node:fs';
import path from 'node:path';
import { parse } from 'acorn';
import * as html from 'parse5';
import { messages } from '../assets/js/core/messages.js';
const root = path.resolve(import.meta.dirname, '..'), required = new Set(), pattern = /[\u3400-\u9fff][^<>${}\n"']*/g;
function collectStatic(value) { for (const key of value.match(pattern) || []) required.add(key.trim()); }
function walk(node, parent = null) {
  if (!node || typeof node !== 'object') return;
  if (node.type === 'TemplateLiteral' && parent?.type === 'TaggedTemplateExpression' && ['ui', '_ui'].includes(parent.tag.name)) node.quasis.forEach(part => collectStatic(part.value.cooked));
  if (node.type === 'Literal' && typeof node.value === 'string' && /[\u3400-\u9fff]/.test(node.value) && parent?.type === 'CallExpression') {
    if (['t', '_tr'].includes(parent.callee.name)) required.add(node.value);
    if (['_static', 'translateStatic'].includes(parent.callee.name)) collectStatic(node.value);
  }
  for (const [key, child] of Object.entries(node)) {
    if (key === 'parentNode') continue;
    if (key === 'attrs') { for (const attr of child) if (attr.name.startsWith('data-i18n')) required.add(attr.value); }
    else if (Array.isArray(child)) child.forEach(value => walk(value, node));
    else if (child && typeof child === 'object') walk(child, node);
  }
}
for (const file of fs.readdirSync(path.join(root, 'assets/js'), { recursive: true }).filter(file => file.endsWith('.js') && file !== 'core/messages.js')) walk(parse(fs.readFileSync(path.join(root, 'assets/js', file), 'utf8'), { ecmaVersion: 'latest', sourceType: 'module' }));
for (const file of fs.readdirSync(root).filter(file => file.endsWith('.html'))) walk(html.parse(fs.readFileSync(path.join(root, file), 'utf8')));
const missing = [...required].filter(key => !messages[key]);
if (missing.length) throw new Error(`Missing English translations: ${JSON.stringify(missing)}`);
console.log(`Verified ${required.size} localized UI messages; user data remains outside the catalog.`);
