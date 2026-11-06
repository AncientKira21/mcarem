#!/usr/bin/env python3
"""Production book export/refresh checks; no client, server or external mod launch."""
from pathlib import Path
import subprocess,tempfile,os,json,re
root=Path(__file__).resolve().parents[1]
base=root/'common/src/main/java/com/ancientkira/mca'
def extract(path,signature):
 text=(base/path).read_text();i=text.index(signature);a=text.index('{',i);depth=1;b=a+1
 while depth:depth+=(text[b]=='{')-(text[b]=='}');b+=1
 return text[i:b]
def shorten(s):
 for prefix in ['com.ancientkira.mca.client.book.','net.minecraft.network.chat.','net.minecraft.server.network.','net.minecraft.world.item.component.','net.minecraft.core.component.','net.minecraft.','com.ancientkira.mca.server.world.data.']:
  s=s.replace(prefix,'')
 return s
page=shorten(extract('client/book/pages/Page.java','public java.util.List<net.minecraft.network.chat.Component> writtenPages('))
text=shorten(extract('client/book/pages/TextPage.java','public List<Component> writtenPages('))
title=shorten(extract('client/book/pages/TitlePage.java','public List<Component> writtenPages('))
listing=shorten(extract('client/book/pages/ListPage.java','protected List<Component> writtenPages(List<Component>'))
dynamic=extract('client/book/pages/DynamicListPage.java','public List<Component> writtenPages(')
book=extract('client/book/Book.java','public List<Component> writtenPages(')
content=shorten(extract('item/ExtendedWrittenBookItem.java','private static net.minecraft.world.item.component.WrittenBookContent writtenContent('))
prepare=shorten(extract('item/ExtendedWrittenBookItem.java','public void prepareLectern('))
getbook=extract('item/ExtendedWrittenBookItem.java','public Book getBook(')
booktext_source=(base/'client/book/BookText.java').read_text()
booktext=shorten(booktext_source[booktext_source.index('    private BookText()'):booktext_source.rfind('}')]).replace('public static MutableComponent translated','public static Component translated')
gson=list((Path.home()/'.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson').glob('*/*/gson-*.jar'))[0]
code='''import java.util.*;import java.util.function.*;import com.google.gson.JsonParser;
class BookText {
'''+booktext+'''
}
record TranslatableContents(String key){String getKey(){return key;}}

enum ChatFormatting{BLACK,WHITE}
class Component{String key;Object getContents(){return key==null?null:new TranslatableContents(key);}static Component translatableWithFallback(String k,String v){Component c=new Component(v);c.key=k;return c;}String text;boolean black;Component(String t){text=t;}static Component empty(){return new Component("");}static Component literal(String s){return new Component(s);}static Component translatable(String s){Component c=new Component(s);c.key=s;return c;}Component copy(){Component c=new Component(text);c.black=black;c.key=key;return c;}Component withStyle(Object style){if(style==ChatFormatting.BLACK)black=true;return this;}Component append(String s){text+=s;return this;}Component append(Component c){return append(c.text);}String getString(){return text;}}
class Page {
'''+page+'''
}
class TextPage extends Page {Component content;Object style=new Object();TextPage(Component c){content=c;}
'''+text+'''
}
class TitlePage extends Page {Component title,subtitle;TitlePage(String t,String s){title=Component.literal(t).withStyle(ChatFormatting.WHITE);subtitle=Component.literal(s);}
'''+title+'''
}
class ListPage extends Page {List<Component> text;int page;ListPage(List<Component> t){text=t;}int getEntriesPerPage(){return 11;}Component writtenTitle(){return Component.literal("Names");}public List<Component> writtenPages(boolean dynamic){return writtenPages(text);}
'''+listing+'''
}
class DynamicListPage extends ListPage {Function<Page,List<Component>> generator;DynamicListPage(Function<Page,List<Component>> g){super(new ArrayList<>());generator=g;}
'''+dynamic+'''
}
class Book {List<Page> pages=new ArrayList<>();String name="family";Component author=Component.literal("Author");String getBookName(){return name;}Component getBookAuthor(){return author;}Book copy(){Book b=new Book();b.pages.addAll(pages);b.name=name;b.author=author;return b;}void addPage(Page p){pages.add(p);}
'''+book+'''
}
record Filterable<T>(T raw){static <T>Filterable<T> passThrough(T value){return new Filterable<>(value);}}
record WrittenBookContent(Filterable<String> title,String author,int generation,List<Filterable<Component>> pages,boolean resolved){}
class DataComponents{static String WRITTEN_BOOK_CONTENT="native";}
class DataComponentsMCA{static String BOOK_PAGES="mca";}
class ItemStack{Map<String,Object> data=new HashMap<>();Object item;ItemStack(Object i){item=i;}@SuppressWarnings("unchecked")<T>T get(String k){return (T)data.get(k);}void set(String k,Object v){data.put(k,v);}}
class Player{}
class ServerPlayer extends Player{Object level(){return null;}}
class Registry{List<Component> lines=List.of(Component.literal("Registry birth"),Component.literal("Registry marriage"));List<Component> getPage(int a,int b){return lines;}}
class Village{Registry registry=new Registry();Optional<Registry> getCivilRegistry(){return Optional.of(registry);}}
class PlayerSaveData{static PlayerSaveData data=new PlayerSaveData();Village village;static PlayerSaveData get(ServerPlayer p){return data;}Optional<Village> getRegistryVillage(Object l){return Optional.ofNullable(village);}}
class ExtendedWrittenBookItem{Book book;ExtendedWrittenBookItem(Book b){book=b;}
'''+content+'\n'+prepare+'\n'+getbook+'''
 static WrittenBookContent nativeContent(Book b){return writtenContent(b,b.writtenPages(false));}
}
class CivilRegistry extends ExtendedWrittenBookItem{CivilRegistry(Book b){super(b);}}
public class Checks{
 static int checks;static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;}
 public static void main(String[]args){
  TextPage text=new TextPage(Component.literal("Text"));var pages=text.writtenPages(false);check(pages.size()==1&&pages.getFirst().getString().equals("Text"),"text preserved");check(pages.getFirst().black,"black text readable on vanilla paper");check(!text.content.black,"export does not alter original page");
  TitlePage title=new TitlePage("Title","Author");check(title.writtenPages(false).getFirst().getString().equals("Title\\n\\nAuthor"),"title and author preserved");check(!title.title.black,"title source style unchanged");
  var entries=new ArrayList<Component>();for(int i=0;i<23;i++)entries.add(Component.literal("Name"+i));ListPage list=new ListPage(entries);list.page=2;var lists=list.writtenPages(false);check(lists.size()==3,"list paginated");check(lists.get(2).getString().contains("Name22"),"last list entry retained");check(list.page==2&&entries.size()==23,"export leaves GUI list state unchanged");
  int[] calls={0};DynamicListPage dyn=new DynamicListPage(p->{calls[0]++;return entries;});check(dyn.writtenPages(false).size()==1&&calls[0]==0,"registration does not call dynamic client resources");check(dyn.writtenPages(true).size()==3&&calls[0]==1,"insertion refreshes dynamic content");check(dyn.text.isEmpty(),"dynamic export leaves GUI cache unchanged");
  Book book=new Book();book.addPage(title);book.addPage(text);book.addPage(dyn);var nativeBook=ExtendedWrittenBookItem.nativeContent(book);check(nativeBook.pages().size()==3&&calls[0]==1,"default content uses static-safe pages");check(nativeBook.resolved()&&nativeBook.generation()==0,"native content already resolved without flattening components");
  ExtendedWrittenBookItem item=new ExtendedWrittenBookItem(book);ItemStack stack=new ItemStack(item);stack.set("custom","keep");item.prepareLectern(stack,null);WrittenBookContent prepared=stack.get(DataComponents.WRITTEN_BOOK_CONTENT);check(prepared.pages().size()==5,"lectern gets expanded dynamic pages");check(stack.item==item&&stack.get("custom").equals("keep"),"original item and unrelated components preserved");
  Book blank=new Book();blank.author=null;blank.name="abcdefghijklmnopqrstuvwxyz0123456789";var empty=ExtendedWrittenBookItem.nativeContent(blank);check(empty.pages().size()==1&&empty.title().raw().length()==32&&empty.author().equals("MCA"),"empty pages and metadata bounds valid");
  Book letterBook=new Book();letterBook.author=null;letterBook.name="letter";ExtendedWrittenBookItem letter=new ExtendedWrittenBookItem(letterBook);ItemStack mail=new ItemStack(letter);mail.set(DataComponentsMCA.BOOK_PAGES,List.of(Component.literal("Mail one"),Component.literal("Mail two")));letter.prepareLectern(mail,null);WrittenBookContent mailContent=mail.get(DataComponents.WRITTEN_BOOK_CONTENT);check(mailContent.pages().size()==2&&mailContent.pages().get(1).raw().getString().equals("Mail two"),"letter payload exported in order");check(((List<?>)mail.get(DataComponentsMCA.BOOK_PAGES)).size()==2&&letterBook.pages.isEmpty(),"letter payload and reusable book untouched");
  CivilRegistry registry=new CivilRegistry(blank);ItemStack record=new ItemStack(registry);registry.prepareLectern(record,new ServerPlayer());WrittenBookContent missing=record.get(DataComponents.WRITTEN_BOOK_CONTENT);check(missing.pages().getFirst().raw().getString().equals("civil_registry.empty"),"missing village gives readable empty registry");PlayerSaveData.data.village=new Village();registry.prepareLectern(record,new ServerPlayer());WrittenBookContent records=record.get(DataComponents.WRITTEN_BOOK_CONTENT);check(records.pages().size()==2&&records.pages().getFirst().raw().getString().equals("Registry birth"),"registry snapshots server-side records");
  check(BookText.title("family").equals("Managing Your Family Vol. XI"),"book title is localized before client language loads");check(BookText.author(Component.translatable("mca.books.family.author")).equals("Leanne the Cleric"),"native metadata author is a name instead of a key");check(BookText.translated("mca.books.family.author").getString().equals("Leanne the Cleric"),"book components have bundled English fallback");check(!BookText.paper(Component.translatable("mca.books.death.0")).getString().contains("§f"),"white legacy text is readable on paper");
  System.out.println("PASS: "+checks+" production lectern book export checks");
 }
}
'''
homes=list((Path.home()/'.gradle/jdks').glob('*/jdk-25*/Contents/Home'));java=Path(os.environ.get('JAVA_HOME',str(homes[0])))/'bin'
with tempfile.TemporaryDirectory(prefix='mca-books-') as d:
 p=Path(d)/'Checks.java';p.write_text(code);subprocess.run([str(java/'javac'),'-cp',str(gson),str(p)],check=True);subprocess.run([str(java/'java'),'-cp',d+':'+str(gson)+':'+str(root/'common/src/main/resources'),'Checks'],check=True)
registry=(base/'registry/ItemsMCA.java').read_text()
expected={'mca:'+name for name in re.findall(r'Item\s+\w+\s*=\s*register\("([^"]+)", new (?:ExtendedWrittenBookItem|CivilRegistry)\(',registry)}
tag=json.loads((root/'common/src/main/resources/data/minecraft/tags/item/lectern_books.json').read_text())
assert set(tag['values'])==expected and tag['replace'] is False
print('PASS: all',len(expected),'MCA book items extend the vanilla lectern tag')
