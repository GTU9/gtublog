import { BlogSidebar } from "@/src/blog-sidebar";
import { Shell } from "@/src/blog-ui";

export default function NotFound() {
  return (
    <Shell title="페이지를 찾을 수 없습니다" description="요청한 글 또는 공개 경로가 존재하지 않습니다." aside={<BlogSidebar />}>
      <p className="muted">주소를 다시 확인하거나 홈으로 돌아가 최근 글을 살펴보세요.</p>
    </Shell>
  );
}
