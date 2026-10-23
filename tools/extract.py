# Read-only extraction of an ext4 image into a folder, plus a manifest of mode/uid/gid/selinux label.
import sys, os, ext4, json
img, out = sys.argv[1], sys.argv[2]
v = ext4.Volume(open(img, 'rb'))
os.makedirs(out, exist_ok=True)
manifest, errors = [], []

def walk(d, rel):
    for ent, ft_ in d.opendir():
        ft = str(ft_).split('.')[-1].split(':')[0]
        name = ent.name_str if hasattr(ent, 'name_str') else ent.name.decode('utf-8', 'replace')
        if name in ('.', '..'):
            continue
        p = rel + '/' + name
        try:
            ino = v.inode_at(p)
            rec = {'path': p, 'type': ft, 'mode': oct(ino.i_mode & 0o7777), 'uid': ino.i_uid, 'gid': ino.i_gid}
            try:
                x = dict(ino.xattrs)
                if 'security.selinux' in x:
                    rec['selinux'] = bytes(x['security.selinux']).rstrip(b'\0').decode()
            except Exception:
                pass
            dest = os.path.join(out, p.lstrip('/'))
            if ft == 'DIR':
                os.makedirs(dest, exist_ok=True)
                manifest.append(rec)
                walk(ino, p)
                continue
            if ft == 'SYMLINK':
                tgt = ino.readlink()
                tgt = tgt.decode('utf-8', 'replace') if isinstance(tgt, bytes) else str(tgt)
                rec['target'] = tgt
                os.symlink(tgt, dest)
            elif ft == 'REG_FILE':
                with open(dest, 'wb') as fo:
                    s = ino.open()
                    while True:
                        b = s.read(1 << 20)
                        if not b:
                            break
                        fo.write(b)
                rec['size'] = ino.i_size
            manifest.append(rec)
        except Exception as e:
            errors.append((p, repr(e)))

walk(v.root, '')
json.dump(manifest, open(out + '.manifest.json', 'w'), indent=0)
open(out + '.errors.txt', 'w').write('\n'.join('%s %s' % e for e in errors))
print('files/entries:', len(manifest), 'errors:', len(errors))
