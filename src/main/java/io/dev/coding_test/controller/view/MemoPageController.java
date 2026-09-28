package io.dev.coding_test.controller.view;

import io.dev.coding_test.common.util.PageRangeUtil;
import io.dev.coding_test.dto.memo.MemoListItem;
import io.dev.coding_test.dto.memo.MemoRequest;
import io.dev.coding_test.dto.memo.MemoResponse;
import io.dev.coding_test.security.core.LoginUserId;
import io.dev.coding_test.service.MemoService;
import io.dev.coding_test.service.MemoSummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 메모 화면(Thymeleaf) 요청을 처리하는 컨트롤러.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/memos")
public class MemoPageController {

    private static final int PAGE_SIZE = 12;
    private static final int PAGE_WINDOW = 5;

    private final MemoService memoService;
    private final MemoSummaryService memoSummaryService;

    /**
     * 메모 목록 페이지를 렌더링한다.
     *
     * @param userId  로그인한 회원 ID
     * @param keyword 제목/본문 검색 키워드 (선택)
     * @param page    페이지 번호 (0부터 시작)
     * @param model   뷰에 전달할 데이터 모델
     * @return 메모 목록 뷰 이름
     */
    @GetMapping
    public String list(@LoginUserId Long userId,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<MemoListItem> memos = memoService.getMemos(userId, keyword, page, PAGE_SIZE);
        model.addAttribute("memos", memos);
        model.addAttribute("keyword", keyword == null ? "" : keyword.strip());
        model.addAttribute("pageNumbers",
                PageRangeUtil.pageNumbers(memos.getNumber(), memos.getTotalPages(), PAGE_WINDOW));
        return "memo/list";
    }

    /**
     * 메모 상세 페이지를 렌더링한다.
     *
     * @param userId 로그인한 회원 ID
     * @param memoId 메모 ID
     * @param model  뷰에 전달할 데이터 모델
     * @return 메모 상세 뷰 이름
     */
    @GetMapping("/{memoId}")
    public String detail(@LoginUserId Long userId,
                         @PathVariable Long memoId, Model model) {
        model.addAttribute("memo", memoService.getMemo(userId, memoId));
        return "memo/detail";
    }

    /**
     * 메모 수정 페이지를 렌더링한다. 기존 제목/본문을 폼에 채워 전달한다.
     *
     * @param userId 로그인한 회원 ID
     * @param memoId 메모 ID
     * @param model  뷰에 전달할 데이터 모델
     * @return 메모 수정 폼 뷰 이름
     */
    @GetMapping("/{memoId}/edit")
    public String editForm(@LoginUserId Long userId,
                           @PathVariable Long memoId, Model model) {
        MemoResponse memo = memoService.getMemo(userId, memoId);
        model.addAttribute("memoId", memoId);
        model.addAttribute("memoRequest", new MemoRequest(memo.title(), memo.content()));
        return "memo/form";
    }

    /**
     * 메모를 수정하고 상세 페이지로 이동한다(PRG).
     * 검증 실패 시 입력값을 유지한 채 수정 폼을 다시 렌더링한다.
     *
     * @param userId             로그인한 회원 ID
     * @param memoId             메모 ID
     * @param request            메모 수정 요청
     * @param bindingResult      검증 결과
     * @param model              뷰에 전달할 데이터 모델
     * @param redirectAttributes 리다이렉트 후 보여줄 메시지
     * @return 상세 페이지 리다이렉트 또는 수정 폼 뷰 이름
     */
    @PostMapping("/{memoId}")
    public String update(@LoginUserId Long userId,
                         @PathVariable Long memoId,
                         @Valid @ModelAttribute("memoRequest") MemoRequest request,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("memoId", memoId);
            return "memo/form";
        }
        memoService.update(userId, memoId, request);
        redirectAttributes.addFlashAttribute("toast", "메모를 수정했어요.");
        return "redirect:/memos/" + memoId;
    }

    /**
     * 메모를 삭제하고 목록 페이지로 이동한다.
     *
     * @param userId             로그인한 회원 ID
     * @param memoId             메모 ID
     * @param redirectAttributes 리다이렉트 후 보여줄 메시지
     * @return 목록 페이지 리다이렉트
     */
    @PostMapping("/{memoId}/delete")
    public String delete(@LoginUserId Long userId,
                         @PathVariable Long memoId, RedirectAttributes redirectAttributes) {
        memoService.delete(userId, memoId);
        redirectAttributes.addFlashAttribute("toast", "메모를 삭제했어요.");
        return "redirect:/memos";
    }

    /**
     * 메모 작성 페이지를 렌더링한다.
     *
     * @param model 뷰에 전달할 데이터 모델
     * @return 메모 작성 폼 뷰 이름
     */
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("memoRequest", new MemoRequest());
        return "memo/form";
    }

    /**
     * 메모를 저장하고 상세 페이지로 이동한다(PRG).
     * 검증 실패 시 입력값을 유지한 채 작성 폼을 다시 렌더링한다.
     *
     * @param userId             로그인한 회원 ID
     * @param request            메모 작성 요청
     * @param bindingResult      검증 결과
     * @param redirectAttributes 리다이렉트 후 보여줄 메시지
     * @return 상세 페이지 리다이렉트 또는 작성 폼 뷰 이름
     */
    @PostMapping
    public String create(@LoginUserId Long userId,
                         @Valid @ModelAttribute("memoRequest") MemoRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "memo/form";
        }
        MemoResponse memo = memoService.create(userId, request);
        redirectAttributes.addFlashAttribute("toast", "메모를 저장했어요.");
        return "redirect:/memos/" + memo.memoId();
    }

    /**
     * 메모 상세 페이지의 AI 요약 패널 fragment를 렌더링한다.
     * 화면 스크립트가 요약 상태가 바뀌었을 때 패널을 교체하는 데 사용한다.
     *
     * @param userId 로그인한 회원 ID
     * @param memoId 메모 ID
     * @param model  뷰에 전달할 데이터 모델
     * @return 요약 패널 fragment
     */
    @GetMapping("/{memoId}/summary")
    public String summaryPanel(@LoginUserId Long userId,
                               @PathVariable Long memoId, Model model) {
        model.addAttribute("memo", memoService.getMemo(userId, memoId));
        return "memo/summary :: panel";
    }

    /**
     * 메모 재요약을 요청하고 상세 페이지로 이동한다. (JavaScript 미사용 환경 대비)
     *
     * @param userId             로그인한 회원 ID
     * @param memoId             메모 ID
     * @param redirectAttributes 리다이렉트 후 보여줄 메시지
     * @return 상세 페이지 리다이렉트
     */
    @PostMapping("/{memoId}/summary")
    public String retrySummary(@LoginUserId Long userId,
                               @PathVariable Long memoId, RedirectAttributes redirectAttributes) {
        memoSummaryService.retry(userId, memoId);
        redirectAttributes.addFlashAttribute("toast", "요약을 다시 요청했어요.");
        return "redirect:/memos/" + memoId;
    }
}
