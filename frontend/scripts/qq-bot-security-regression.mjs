import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const policySource = fs.readFileSync(path.join(root, 'src/security/qqBotAccess.ts'), 'utf8');
const output = ts.transpileModule(policySource, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 },
}).outputText;
const { canManageQqBot } = await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}`);

for (const role of ['super_admin', 'supper_admin']) {
  assert.equal(canManageQqBot({ role, accountStatus: 'normal', userAccount: 'custom-super' }), true);
  for (const accountStatus of [undefined, '', 'pending', 'banned', 'disabled']) {
    assert.equal(canManageQqBot({ role, accountStatus }), false);
  }
}
for (const role of ['stu_admin', 'department', 'school_admin', 'user', 'admin', 'SUPER_ADMIN', '']) {
  assert.equal(canManageQqBot({
    role, accountStatus: 'normal', userAccount: 'admin',
    permissions: ['manage_sys', 'manage_api', 'manage_permission', 'manage_qq_bot'],
  }), false, `Permission grants must not bypass the super-admin role for ${role}`);
}
assert.equal(canManageQqBot(null), false);
assert.equal(canManageQqBot(undefined), false);

// 两处准入必须复用同一策略；路由守卫必须在内置管理员的全权限分支之前执行。
const layout = fs.readFileSync(path.join(root, 'src/app/admin/AdminLayoutClient.tsx'), 'utf8');
assert.match(layout, /if \(canManageQqBot\(user\)\) systemItems\.push\(getItem\('QQ 机器人管理', '\/admin\/qq-bot'\)\)/);
const permissionCheck = layout.slice(layout.indexOf('const checkPagePermission'));
assert.ok(permissionCheck.indexOf("path === '/admin/qq-bot'") < permissionCheck.indexOf('if (isAdminUser) return true'));
assert.match(permissionCheck, /path\.startsWith\('\/admin\/qq-bot\/'\)\) return canManageQqBot\(user\)/);

const manager = fs.readFileSync(path.join(root, 'src/app/admin/_legacy/QqBotManager.tsx'), 'utf8');
assert.match(manager, /if \(!authorized\) return <Result status="403"/);
assert.match(manager, /accessToken: '', hydroApiKey: ''/);
assert.match(manager, /accessToken: values\.accessToken\?\.trim\(\) \|\| null/);
assert.match(manager, /hydroApiKey: values\.hydroApiKey\?\.trim\(\) \|\| null/);
console.log('QQ bot security regression passed: super-admin-only menu and route, account status, permission bypass denial, credential form handling.');
