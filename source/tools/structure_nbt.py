import struct,gzip,io
class Reader:
 def __init__(self,b):self.f=io.BytesIO(gzip.decompress(b) if b[:2]==b'\x1f\x8b' else b)
 def n(self,fmt):return struct.unpack('>'+fmt,self.f.read(struct.calcsize('>'+fmt)))[0]
 def text(self):return self.f.read(self.n('H')).decode()
 def payload(self,t):
  if t in (1,2,3,4,5,6):return self.n({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return self.f.read(self.n('i'))
  if t==8:return self.text()
  if t==9:
   subtype=self.n('b');return subtype,[self.payload(subtype) for _ in range(self.n('i'))]
  if t==10:
   result={}
   while True:
    subtype=self.n('b')
    if subtype==0:return result
    name=self.text();result[name]=(subtype,self.payload(subtype))
  if t in (11,12):return [self.n('i' if t==11 else 'q') for _ in range(self.n('i'))]
  raise ValueError(t)
 def read(self):t=self.n('b');self.text();return self.payload(t)
def encode(root):
 out=io.BytesIO()
 def num(f,v):out.write(struct.pack('>'+f,v))
 def txt(v):b=v.encode();num('H',len(b));out.write(b)
 def emit(t,v):
  if t in (1,2,3,4,5,6):num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t],v)
  elif t==7:num('i',len(v));out.write(v)
  elif t==8:txt(v)
  elif t==9:
   st,vs=v;num('b',st);num('i',len(vs))
   for child in vs:emit(st,child)
  elif t==10:
   for k,(st,child) in v.items():num('b',st);txt(k);emit(st,child)
   num('b',0)
  elif t in (11,12):
   num('i',len(v))
   for child in v:num('i' if t==11 else 'q',child)
  else:raise ValueError(t)
 num('b',10);txt('');emit(10,root);return gzip.compress(out.getvalue(),mtime=0)
def value(tag,k):return tag[k][1]
def pos(block):return tuple(value(block,'pos')[1])
