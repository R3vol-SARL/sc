package com.github._1element.controller;

import com.github._1element.domain.Camera;
import com.github._1element.dto.ImagesSummaryResult;
import com.github._1element.domain.SurveillanceImage;
import com.github._1element.repository.CameraRepository;
import com.github._1element.repository.SurveillanceImageRepository;
import com.github._1element.service.SurveillanceService;
import com.github._1element.utils.RequestUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.format.annotation.DateTimeFormat.*;

@Controller
public class SurveillanceController {

  @Autowired
  private SurveillanceService surveillanceService;

  @Autowired
  private SurveillanceImageRepository imageRepository;

  @Autowired
  private CameraRepository cameraRepository;

  @Value("${sc.view.images-per-page:50}")
  private Integer pageSize;

  private static final String URI_RECORDINGS = "/recordings";

  private static final String URI_LIVEVIEW = "/liveview";

  private static final String URI_LIVESTREAM = "/livestream";

  private static final String PATH_SEPARATOR = "/";

  private static final String SORT_FIELD = "receivedAt";

  private static final String DEFAULT_CAMERA_DISPLAY_NAME = "alle Kameras";

  @RequestMapping(value = {URI_RECORDINGS, URI_RECORDINGS + "/{date}"}, method = RequestMethod.GET)
  public String recordingsList(@PathVariable @DateTimeFormat(iso = ISO.DATE) Optional<LocalDate> date,
                               @RequestParam Optional<String> camera, @RequestParam Optional<Boolean> archive,
                               @RequestParam Optional<Integer> page, Model model, Pageable pageable) throws Exception {

    Integer zeroBasedPageNumber = 0;
    if (page.isPresent()) {
      zeroBasedPageNumber = page.get() - 1;
    }

    boolean isArchive = false;
    if (archive.isPresent() && Boolean.TRUE.equals(archive.get())) {
      isArchive = true;
    }

    PageRequest pageRequest = new PageRequest(
      zeroBasedPageNumber,
      pageSize,
      new Sort(new Sort.Order(Sort.Direction.DESC, SORT_FIELD))
    );

    String currentCameraId = null;
    String currentCameraName = DEFAULT_CAMERA_DISPLAY_NAME;
    Camera currentCamera = surveillanceService.getCamera(camera);
    if (currentCamera != null) {
      currentCameraName = currentCamera.getName();
      currentCameraId = currentCamera.getId();
    }

    Page<SurveillanceImage> images = surveillanceService.getImagesPage(camera, date, isArchive, pageRequest);
    LocalDateTime mostRecentImageDate = surveillanceService.getMostRecentImageDate(images, date);
    String visibleImageIds = images.getContent().stream().map(i -> String.valueOf(i.getId())).collect(Collectors.joining(","));

    List<ImagesSummaryResult> imagesSummary = imageRepository.getImagesSummary();
    Long countAllImages = imageRepository.countAllImages();

    List<Camera> cameras = cameraRepository.findAll();

    String baseUrl = URI_RECORDINGS;
    if (date.isPresent()) {
      baseUrl = baseUrl + PATH_SEPARATOR + date.get().toString();
    }

    // recordings
    model.addAttribute("images", images);
    model.addAttribute("cameras", cameras);
    model.addAttribute("mostRecentImageDate", mostRecentImageDate);
    model.addAttribute("baseUrl", baseUrl);
    model.addAttribute("isArchive", isArchive);
    model.addAttribute("currentCameraId", currentCameraId);
    model.addAttribute("currentCameraName", currentCameraName);
    model.addAttribute("visibleImageIds", visibleImageIds);

    // navigation
    model.addAttribute("countRecordings", countAllImages);
    model.addAttribute("recordingsNavigation", imagesSummary);

    return "recordings";
  }

  @RequestMapping(value = URI_RECORDINGS, method = RequestMethod.POST)
  public String recordingsArchive(@RequestParam List<Long> imageIds) throws Exception {
    imageRepository.archiveByIds(imageIds);

    return "redirect:" + URI_RECORDINGS;
  }

  @RequestMapping(value = {URI_LIVEVIEW}, method = RequestMethod.GET)
  public String liveview(Model model, HttpServletRequest request) throws Exception {
    List<Camera> cameras = cameraRepository.findAll();

    model.addAttribute("cameras", cameras);
    model.addAttribute("liveviewUrl", URI_LIVEVIEW);

    if (RequestUtil.isAjax(request)) {
      return "fragments/liveview-grid";
    }

    return "liveview";
  }

  @RequestMapping(value = {URI_LIVEVIEW + "/{cameraId}"}, method = RequestMethod.GET)
  public String liveviewSingleCamera(@PathVariable Optional<String> cameraId, Model model, HttpServletRequest request) throws Exception {
    Camera camera = surveillanceService.getCamera(cameraId);
    if (camera == null) {
      throw new Exception("Camera not found.");
    }

    model.addAttribute("camera", camera);

    if (RequestUtil.isAjax(request)) {
      return "fragments/liveview-camera";
    }

    model.addAttribute("liveviewAjaxUrl", URI_LIVEVIEW);

    return "liveview-single";
  }

  @RequestMapping(value = {URI_LIVESTREAM}, method = RequestMethod.GET)
  public String livestream(Model model, HttpServletRequest request) throws Exception {
    List<Camera> cameras = cameraRepository.findAll();

    model.addAttribute("cameras", cameras);
    model.addAttribute("livestreamUrl", URI_LIVESTREAM);

    return "livestream";
  }

  @RequestMapping(value = {URI_LIVESTREAM + "/{cameraId}"}, method = RequestMethod.GET)
  public String livestreamSingleCamera(@PathVariable Optional<String> cameraId, Model model, HttpServletRequest request) throws Exception {
    Camera camera = surveillanceService.getCamera(cameraId);
    if (camera == null) {
      throw new Exception("Camera not found.");
    }

    model.addAttribute("camera", camera);

    return "livestream-single";
  }

}