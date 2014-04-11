from  shext import LocateCmd
import fnmatch
import os
import re
import vim
import subprocess
import os.path

if "EditUtil" not in globals() :
    from jde import EditUtil

if "Parser" not in globals() :
    from jde import Parser

class Prompt(object) :
    def __init__(self, init_prompt_value):
        self.key_strokes = []
        if init_prompt_value :
            self.key_strokes.append(init_prompt_value)

    def get_name(self):
        return "".join(self.key_strokes)

    def append(self,char):
        self.key_strokes.append(char)

    def delete_last(self):
        self.key_strokes = self.key_strokes[0:-1]

    def show(self):
        vim.command("echo '%s%s'" % (">> ", self.get_name() ))

class QuickLocater(object) :
    def __init__(self, init_prompt_value,content_manager) :
        self.content_manager = content_manager
        self.prompt = Prompt(init_prompt_value)

    def show_matched_result(self):
        pat = self.prompt.get_name() + "*"
        result = []
        #if len(pat) > 2 :
        result = self.content_manager.search_content(pat)
        output(result)
        win_height = len(result)
        vim.command("resize %s" % str(win_height) )
        if isinstance(self.content_manager,EditHistoryManager):
            self.content_manager.cursor_current_buf()


    def save_env(self):
        self.timeoutlen = vim.eval("&timeoutlen")
        self.insertmode = vim.eval("&insertmode")
        self.showcmd = vim.eval("&showcmd")
        self.report = vim.eval("&report")
        self.sidescroll = vim.eval("&sidescroll")
        self.sidescrolloff = vim.eval("&sidescrolloff")
        self.guicursor = vim.eval("&guicursor")
        self.cursor_bg = vim.eval("""synIDattr(synIDtrans(hlID("Cursor")), "bg")""")
        self.last_winnr = vim.eval("winnr()")
        if self.cursor_bg == None :
            self.cursor_bg = "white"

        #save each window height
        winnr = vim.eval("winnr('$')")
        self.winheights = []
        for i in range(1,int(winnr)):
            self.winheights.append(vim.eval("winheight('%s')" % str(i)))

    def restore_env(self):
        vim.command("set timeoutlen=%s" % self.timeoutlen)

        if self.insertmode == "0" :
            vim.command("set noinsertmode")
        else :
            vim.command("set insertmode")

        if self.showcmd == "0":
            vim.command("set noshowcmd")
        else :
            vim.command("set showcmd")

        vim.command("set report=%s" % self.report )
        vim.command("set sidescroll=%s" % self.sidescroll)
        vim.command("set sidescrolloff=%s" % self.sidescrolloff) 

        vim.command("set guicursor=%s" % self.guicursor)
        vim.command("highlight Cursor guifg=black guibg=%s" % (self.cursor_bg))


    def restore_winsize(self):
        for i in range(0,len(self.winheights)):
            vim.command("exec '%s wincmd w'" % str(i+1) )
            vim.command("resize %s" % self.winheights[i])

    @staticmethod
    def runApp(content_manager ):
        global quickLocater
        
        name = content_manager.get_init_prompt()
        quickLocater = QuickLocater(name,content_manager)
        quickLocater.create_explorer_buffer()
        if content_manager.show_on_open or name.strip() != ""  :
            quickLocater.prompt.show()
            quickLocater.show_matched_result()

    def create_explorer_buffer(self) :
        self.save_env()
        vim.command("silent! keepalt botright 1split explorer_buffer")
        vim.command("setlocal bufhidden=delete")
        vim.command("setlocal buftype=nofile")
        vim.command("setlocal noswapfile")
        vim.command("setlocal nowrap")
        vim.command("setlocal nonumber")
        vim.command("setlocal foldcolumn=0")
        vim.command("setlocal nocursorline")
        vim.command("setlocal nospell")
        vim.command("setlocal nobuflisted")
        vim.command("setlocal textwidth=0")
        vim.command("setlocal noreadonly")
        vim.command("setlocal cursorline")

        vim.command("set timeoutlen=0")
        vim.command("set noinsertmode")
        vim.command("set noshowcmd")
        vim.command("set nolist")
        vim.command("set report=9999")
        vim.command("set sidescroll=0")
        vim.command("set sidescrolloff=0")

        vim.command("set guicursor+=a:blinkon0")
        bg = vim.eval("""synIDattr(synIDtrans(hlID("Normal")), "bg")""")
        if bg :
            vim.command("highlight Cursor guifg=black guibg=%s" % (bg))
        

        printables = """/!"#$%&'()*+,-.0123456789:<=>?#@"ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_abcdefghijklmnopqrstuvwxyz{}~"""
        mapcmd = "noremap <silent> <buffer>"

        for byte in printables :
            vim.command("%s %s :python quickLocater.on_key_pressed('%s')<CR>" % (mapcmd, byte , byte))

        vim.command("%s  <Tab>    :python quickLocater.on_key_pressed('%s')<cr>" %(mapcmd, "Tab"))
        vim.command("%s  <BS>     :python quickLocater.on_key_pressed('%s')<cr>" %(mapcmd, "BS"))
        vim.command("%s  <Del>    :python quickLocater.on_key_pressed('%s')<cr>" %(mapcmd, "Del"))
        vim.command("%s  <CR>     :python quickLocater.on_key_pressed('%s')<cr>" %(mapcmd, "CR"))
        vim.command("%s  <Esc>    :python quickLocater.on_key_pressed('%s')<cr>" %(mapcmd, "ESC"))
        vim.command("%s  <C-j>    :python quickLocater.on_cursor_move('down')<cr>" %(mapcmd ))
        vim.command("%s  <C-k>    :python quickLocater.on_cursor_move('up')<cr>" %(mapcmd ))
        vim.command("%s  <C-d>    :python quickLocater.on_remove_content()<cr>" %(mapcmd ))
        vim.command("%s  <C-B>    :python quickLocater.open_content('buffer')<cr>" %(mapcmd ))
        vim.command("%s  <C-T>    :python quickLocater.open_content('tab')<cr>" %(mapcmd ))
        vim.command("%s  <C-Y>    :python quickLocater.yank_content()<cr>" %(mapcmd ))
        vim.command("%s  <C-v>    :python quickLocater.on_paste_content()<cr>" %(mapcmd ))

    def on_paste_content(self):
        content = vim.eval("getreg('+')")
        content = content.replace("\n","").strip()
        self.prompt.append(content)
        self.prompt.show()
        self.show_matched_result()

    def on_cursor_move(self, direction) :
        work_buffer = vim.current.buffer
        win = vim.current.window
        row,col = win.cursor
        if direction == "up" :
            if row > 1 : win.cursor = ( row-1 , col)
        else :
            if row < len(work_buffer) : win.cursor = ( row+1 , col)

    def on_remove_content(self):
        if isinstance(self.content_manager,EditHistoryManager):
            self.content_manager.on_remove_content()

    def yank_content(self):
        work_buffer=vim.current.buffer
        row,col = vim.current.window.cursor
        content = re.escape( work_buffer[row-1])
        vim.command('let @@="%s"' % content )
        print "line has been yanked."


    def open_content(self,mode="local"):
        "mode  { local, buffer, tab }"
        work_buffer=vim.current.buffer
        row,col = vim.current.window.cursor
        line = work_buffer[row-1]
        self.clean()
        self.content_manager.open_content(line, mode)
        if mode == "local":
            self.restore_winsize()
            vim.command("exec '%s wincmd w'" % self.last_winnr)

    def on_key_pressed(self, key):
        if key == "Tab" :
            pass
        elif key == "BS" or key == "Del":
            self.prompt.delete_last()
            self.prompt.show()
            self.show_matched_result()
        elif key == "CR" :
            self.open_content()
        elif key == "ESC" :
            self.clean()
        else :
            self.prompt.append(key)
            self.prompt.show()
            self.show_matched_result()

    def clean(self):
        vim.command("bwipeout")
        vim.command("echo ''")
        self.restore_env()
        self.restore_winsize()
        vim.command("exec '%s wincmd w'" % self.last_winnr)

class FileContentManager(object):

    def __init__(self, locateType):
        self.bound_chars = """/\?%*:|"<>(), \t\n"""
        shext_locatedb_path = os.path.join(SzToolsConfig.getDataHome(), "locate.db")
        self.locatecmd = LocateCmd(shext_locatedb_path)
        self.locateType = locateType
        self.show_on_open = False

    def get_init_prompt(self):
        buf = vim.current.buffer
        row, col = vim.current.window.cursor
        line = buf[row-1]
        if line.strip() == "" :
            return ""
        result = []
        for i in range(col,-1,-1):
            char = line[i]
            if char in self.bound_chars :
                break
            result.insert(0,char)
        for i in range(col+1,len(line)):
            char = line[i]
            if char in self.bound_chars :
                break
            result.append(char)
        return "".join(result)

    def search_content(self,search_pat):
        cur_dir = os.getcwd()
        result = self.locatecmd.locateFile(search_pat,startWithAlias=True)

        rows = []
        self.start_dirs = {}
        for apath,alias,start_dir in result :
            if self.locateType == "all" :
                rows.append(apath)
            else :
                rtl_path = apath[apath.find(os.path.sep)+1:]
                abpath = os.path.join(start_dir, rtl_path)
                if cur_dir in abpath :
                    rows.append(apath)
            self.start_dirs[alias] = start_dir
        rows = rows[0:30]
        return rows

    def open_content(self,line,mode="local"):
        fname = line.strip()
        if not fname : return 

        alias = fname[0: fname.find(os.path.sep)]
        rtl_path = fname[fname.find(os.path.sep)+1:]
        fname = os.path.join(self.start_dirs[alias], rtl_path)

        bin_file_exts = ["doc","docx","ppt","xls","png","jpg","gif","bmp","zip","jar",\
                "war","rar","pdf","psd"]
        basename,ext = os.path.splitext(fname)
        if  len(ext) > 1 and ext[1:] in bin_file_exts :
            if sys.platform.startswith('darwin'):
                subprocess.Popen('open "%s"' % fname,shell=True)
            elif os.name == 'nt':
                os.startfile(fname)
            elif os.name == 'posix':
                subprocess.Popen('xdg-open "%s"' % fname,shell=True)
        else :
            if mode == "local" :
                vim.command("edit %s "  %(fname))
            elif mode == "buffer" :
                vim.command("split %s "  %(fname))
            elif mode == "tab" :
                vim.command("tabedit %s "  %(fname))

class JavaMemberContentManager(object):

    def __init__(self):
        work_buffer=vim.current.buffer
        self.memberInfo = Parser.parseAllMemberInfo(work_buffer)
        self.show_on_open = True

    def get_init_prompt(self):
        name = ""
        return name

    def search_content(self,search_pat):
        result = []
        if not search_pat :
            search_pat = "*"
        pat = re.compile("^%s.*" % search_pat.replace("*",".*") , re.IGNORECASE)
        for name ,mtype,rtntype,param,lineNum in self.memberInfo :
            if pat.match(name) :
                if mtype == "method" :
                    tipStr = "%s(%s) : %s " % (name,param,rtntype)
                else :
                    tipStr = name
                result.append("\t".join((tipStr,str(lineNum))))
        return result

    def open_content(self,line,mode):
        name,lineNum = line.split("\t")
        vim.command("normal %sG" % str(lineNum))
        
class TypeHierarchyContentManager(object):

    def __init__(self,source_file, memberName, params):
        work_buffer=vim.current.buffer
        self.hierarchy = EditUtil.getTypeHierarchy().split("\n")
        self.source_file = source_file
        self.memberName = memberName
        self.param_count = -1
        if params != None :
            self.param_count = len(params.split(",")) if params.strip() != "" else 0
        self.show_on_open = True

    def get_init_prompt(self):
        name = ""
        return name

    def search_content(self,search_pat):
        result = []
        if not search_pat :
            search_pat = "*"

        pat = re.compile("^%s.*" % search_pat.replace("*",".*") , re.IGNORECASE)
        for line in self.hierarchy :
            if pat.match(line.strip()) :
                result.append(line)
        return result

    def open_content(self,line,mode):
        line = line.strip()
        binName = line[0:line.find("-")].strip()
        pkgName = line[line.find("-")+1:].strip() 
        if pkgName == "" :
            defClassName = binName
        else :
            defClassName = pkgName +"."+ binName

        EditUtil.searchAndEdit(self.source_file, defClassName,self.memberName,mode,self.param_count)

class JavaClassNameContentManager(object):

    def __init__(self):
        self.current_file_name = vim.current.buffer.name
        if not self.current_file_name :
            cur_dir = os.getcwd()
            self.current_file_name = os.path.join(cur_dir,"fake")
        self.classPathXml = ProjectManager.getClassPathXml(self.current_file_name)
        self.show_on_open = False

    def get_init_prompt(self):
        buffer = vim.current.buffer
        pat = re.compile("[\w\.]")
        row, col = vim.current.window.cursor
        row_len = len(buffer[row-1])
        start_index = 0 
        end_index = row_len
        if buffer[row-1].strip() == "" :
            return ""
        for i in range(col,-1,-1):
            if not pat.match(buffer[row-1][i]) :
                start_index = i
                break
        for j in range(col,row_len):
            if not  pat.match(buffer[row-1][j]) :
                end_index = j
                break
        return buffer[row-1][start_index+1:end_index]

    def search_content(self,search_pat):
        search_pat = search_pat[:-1]
        if not search_pat :
            return []
        classNameList = Talker.getClassList(search_pat,self.classPathXml,ignoreCase="true",withLoc="true").split("\n")
        return classNameList

    def open_content(self,line,mode):
        line = line.strip()
        className = line[0:line.find(" ")]
        EditUtil.searchAndEdit(self.current_file_name, className,"",mode)

class EditHistoryManager(object):
    def __init__(self):
        self.file_history = edit_history.get_history()
        self.matched_item = []
        self.show_on_open = True
        self.cur_buf = vim.current.buffer.name

    def get_init_prompt(self):
        return ""

    def search_content(self,search_pat):
        rows = []
        self.matched_item = []
        if self.file_history == None or len(self.file_history)==0 :
            self.matched_item = []
            return rows
        self.start_dirs = {}
        if not search_pat :
            search_pat = "*"
        pat = re.compile(".*%s.*" % search_pat.replace("*",".*") , re.IGNORECASE)
        maxlen = max([len(os.path.basename(path)) for path in self.file_history])
        for path in self.file_history :
            basename = os.path.basename(path)
            if pat.match(basename):
                self.matched_item.append(path)
                rows.append("%s\t%s" %(basename.ljust(maxlen),path))
        return rows

    def open_content(self,line,mode="local"):
        line = line.strip()
        if line == "" :
            return
        basename,path = re.split("\t",line)

        if mode == "local" :
            vim.command("edit %s "  %(path))
        elif mode == "buffer" :
            vim.command("split %s "  %(path))
        elif mode == "tab" :
            vim.command("tabedit %s "  %(path))

    def cursor_current_buf(self):
        if self.cur_buf == None :
            return 
        cur_name = self.cur_buf.replace("\\","/")
        if cur_name in self.matched_item :
            vim.current.window.cursor = ( self.matched_item.index(cur_name)+1, 0)

    def on_remove_content(self):
        work_buffer=vim.current.buffer
        row,col = vim.current.window.cursor
        line = work_buffer[row-1].strip()
        basename,path = re.split("\s+",line)
        bufnr = vim.eval("bufnr('%s')" % path)    
        if bufnr != "-1" :
            vim.command('Bclose %s' % bufnr)
            del work_buffer[row-1]
            vim.command("resize %d" % len(work_buffer))


