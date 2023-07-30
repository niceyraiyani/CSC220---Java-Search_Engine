package prog11;

import javax.sql.rowset.serial.SQLOutputImpl;
import java.util.*;

public class Goggle implements SearchEngine{
    Disk<PageFile> pageDisk;
    Map<String, Long> urlIndex;
    Disk<List<Long>> wordDisk;
    Map<String, Long> wordIndex;

    Goggle(){
        pageDisk = new Disk<PageFile>();
        urlIndex = new TreeMap<>();
        wordDisk = new Disk<List<Long>>();
        wordIndex = new HashMap<>();
    }

    Long indexPage(String url){
        Long index = pageDisk.newFile();
        PageFile page = new PageFile(index, url);
        pageDisk.put(index , page);
        urlIndex.put(url, index);
        System.out.println("indexing page " + page);
        return index;
    }

    public Long indexWord (String word){
        Long index = wordDisk.newFile();
        List<Long> page = new ArrayList<Long>();
        wordDisk.put(index, page);
        wordIndex.put(word,index);
        System.out.println("Indexing word " +index + "(" + word + ")" +page);
        System.out.println("Added page index " + index + "(" + word + ")" + page + pageDisk.get(index));
        return index;
    }

    @Override
    public void collect (Browser browser, List<String> startingURLs) {
        Queue<Long> pageIndices = new ArrayDeque<Long>();
        for (String url : startingURLs) {
            if (!urlIndex.containsKey(url)) {
                Long index = indexPage(url);
                pageIndices.offer(index);
            }
        }
        while (!pageIndices.isEmpty()) {
            System.out.println("queue " + pageIndices);
            Long index = pageIndices.poll();
            PageFile file = pageDisk.get(index);
            System.out.println("Dequeued " + file);
            if (browser.loadPage(file.url)) {
                List<String> urls = browser.getURLs();
                System.out.println("URLs " + urls);
                for (String url : browser.getURLs()) {
                    if (!urlIndex.containsKey(url)) {
                        Long newIndex = indexPage(url);
                        pageIndices.offer(newIndex);
                        file.indices.add(newIndex);
                    } else {
                        file.indices.add(urlIndex.get(url));
                    }
                }
                System.out.println("Words: " + browser.getWords());
                List<String> words = browser.getWords();
                for (String word : words) {
                    if (!wordIndex.containsKey(word)) {
                        indexWord(word);
                    }
                    Long index2 = wordIndex.get(word);
                    List<Long> wordFile = wordDisk.get(index2);
                    if (wordFile.size() == 0 || !wordFile.get(wordFile.size() - 1).equals(index)) {
                        wordFile.add(index);
                    }
                }
                System.out.println("Updated " + file);
            }
        }
    }
    void rankSlow(){
        for(PageFile file : pageDisk.values()){
            file.priority.add(1.0);

        }
        for(int i =1; i <20; i++){
            for(PageFile file : pageDisk.values()){
                System.out.println(file);
                file.priority.add(0.0);
            }
            for(PageFile file : pageDisk.values()){
                double influence = file.priority.get(file.priority.size()-2);
                double changeToPriority = influence / file.indices.size();
                for(Long linkedInd : file.indices){
                    PageFile linked = pageDisk.get(linkedInd);
                    double currentPrio = linked.priority.get(linked.priority.size() - 1);
                    linked.priority.set(linked.priority.size()-1, currentPrio + changeToPriority);
                }
            }
        }
    }

    void rankFast () {
        System.out.println("rank ");

        for (PageFile file : pageDisk.values())
            file.priority.add(1.0);

        for (int i = 1; i < 20; i++) {
            for (PageFile file : pageDisk.values())
                System.out.println(file);

            System.out.println("-----------------");
            List<Vote> voteList = new ArrayList<Vote>();

            for (PageFile file : pageDisk.values()) {
                double fractionalVote = file.priority.get(i - 1) / file.indices.size();
                for (Long index : file.indices)
                    voteList.add(new Vote(index, fractionalVote));
            }

            Collections.sort(voteList);
            Iterator<Vote> iter = voteList.iterator();
            Vote vote = null;

            if(iter.hasNext())
                vote = iter.next();

            for(PageFile file : pageDisk.values()) {
                double totalVote = 0;
                while (vote != null && vote.index.equals(file.index)) {
                    totalVote += vote.vote;
                    if(iter.hasNext())
                        vote = iter.next();
                    else
                        vote = null;
                }
                file.priority.add(totalVote);
            }
        }
    }

    @Override
    public String[] search(List<String> searchWords, int numResults) {
        Iterator<Long>[] wordFileIterators = (Iterator<Long>[]) new Iterator[searchWords.size()];
        long[] curentPageIndexes;
        for(int i=0; i < searchWords.size(); i++){
            String word = searchWords.get(i);
            Long index = wordIndex.get(word);
            List<Long> x = wordDisk.get(index);
            wordFileIterators[i] = x.iterator();
        }

        long[] currentPageIndexes = new long[searchWords.size()];
        PriorityQueue<Long> bestPageIndices = new PriorityQueue(new PageComparator());
        while(getNextPageIndexes(currentPageIndexes, wordFileIterators)){
            if(allEqual(currentPageIndexes)){
                long index = currentPageIndexes[0];
                PageFile page = pageDisk.get(index);
                String url = page.url;
                System.out.println(url);
                if(bestPageIndices.size() < numResults){
                    bestPageIndices.offer(index);
                } else if(pageDisk.get(index).getIndexCount()>pageDisk.get(bestPageIndices.peek()).getIndexCount()) {
                    bestPageIndices.poll();
                    bestPageIndices.offer(index);
                }
            }
        }

        String[] results = new String[bestPageIndices.size()];
        for(int i = results.length; --i>=0;){
            results[i]=pageDisk.get(bestPageIndices.poll()).url;
        }
        return results;
    }


    private boolean allEqual (long[] array) {
        if(array.length == 0){
            return true;
        }
        long first = array[0];
        for(int i =1; i < array.length; i++){
            if(array[i] != first){
                return false;
            }
        }
        return true;
    }


    private long getLargest (long[] array){
        long largest = array[0];
        for(int i =0; i < array.length; i++){
            if(array[i] > largest){
                largest = array[i];
            }
        }
        return largest;
    }

    private boolean getNextPageIndexes (long[] currentPageIndexes, Iterator<Long>[] wordFileIterators){
        long largest = getLargest(currentPageIndexes);
        if(allEqual(currentPageIndexes)){
            largest++;
        }

        for(int i = 0; i< currentPageIndexes.length; i++){
            long index = currentPageIndexes[i];
            if(index != largest){
                if(wordFileIterators[i].hasNext()) {
                    currentPageIndexes[i] = wordFileIterators[i].next();
                } else{
                    return false;
                }

            }
        }
        return true;
    }

    public class Vote implements Comparable<Vote>{

        Long index;
        double vote;
        public Vote(Long index, double vote) {
            this.index = index;
            this.vote = vote;
        }

        @Override
        public int compareTo(Vote o) {
            return this.index.compareTo(o.index);
        }
    }

    public class PageComparator implements Comparator<Long>{
        @Override
        public int compare(Long o1, Long o2) {
            return pageDisk.get(o1).getIndexCount()-pageDisk.get(o2).getIndexCount();
        }
    }
}
